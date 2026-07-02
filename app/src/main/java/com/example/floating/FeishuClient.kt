package com.example.floating

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * 飞书远程控制 —— 无服务器：手机直连飞书开放平台 REST。
 * 出站 sendText（孩子快捷回复/回执），入站 pollNewMessages（家长在群里的指令/消息）。
 * 配置写死（个人专用机）。Secret 编译进 APK 可被提取，介意可在飞书后台重置。
 */
object FeishuClient {
    // 配置从 BuildConfig 注入（值在 gitignore 的 feishu.properties，不进 git）
    private val APP_ID = BuildConfig.FEISHU_APP_ID
    private val APP_SECRET = BuildConfig.FEISHU_APP_SECRET
    private val CHAT_ID = BuildConfig.FEISHU_CHAT_ID
    private const val BASE = "https://open.feishu.cn/open-apis"

    const val PREFS = "FeishuPrefs"
    const val KEY_LAST_TS = "lastMsgTs"        // 已处理消息的最大 create_time(ms)
    const val KEY_PAUSE_UNTIL = "pauseUntil"   // 暂停锁屏到此时间(ms)
    const val KEY_PENDING_MSG = "pendingMsg"   // 未锁屏时收到的家长消息，下次锁屏展示
    const val KEY_HELP_SENT = "helpSent"       // 帮助消息只发一次

    const val HELP_TEXT =
        "🤖 小欣管家·遥控指令（直接在本群发文字即可）：\n" +
        "• 解锁30 — 立即解锁 30 分钟（不带数字默认 30，上限 240）\n" +
        "• 锁定 — 立即出题锁屏\n" +
        "• 停用60 — 60 分钟内不锁，给自由时间（默认 60）\n" +
        "• 次数3 — 设置每天可答题解锁的次数（默认 3，0~20；家长远程解锁不计入）\n" +
        "• 加一次 — 临时多给今天 1 次答题机会（仅今天有效，可带数字如“加2次”）\n" +
        "• 重置次数 — 清零今天已用的解锁次数\n" +
        "• help / 帮助 — 查看本指令清单\n" +
        "• 其它任意话（如“宝贝加油”）— 显示给小欣看，她可一键回复\n" +
        "⏱ 生效：她锁屏时约 45 秒；没锁屏时最长约 15 分钟。"

    // 首次连通时发一条帮助消息（成功才置标志，失败下次再试），只发一次
    fun sendHelpOnce(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_HELP_SENT, false)) return
        if (sendText(HELP_TEXT)) prefs.edit().putBoolean(KEY_HELP_SENT, true).apply()
    }

    private var token: String = ""
    private var tokenExpireAt: Long = 0L

    // ===== 指令模型 =====
    sealed class Command {
        data class UnlockNow(val minutes: Int) : Command()
        object LockNow : Command()
        data class PauseLock(val minutes: Int) : Command()
        data class SetUnlockLimit(val limit: Int) : Command()
        data class GrantExtra(val times: Int) : Command()
        object ResetUnlocks : Command()
        object Help : Command()
        data class MessageToChild(val text: String) : Command()
    }

    /** 纯函数：把家长文本解析成指令（可单测）。先去掉 @提及。 */
    fun parseCommand(raw: String): Command {
        val t = raw.replace(Regex("@_\\w+"), "").trim()
        val num = Regex("(\\d+)").find(t)?.value?.toIntOrNull()
        return when {
            t.equals("help", true) || t == "帮助" || t == "指令" || t == "菜单" || t == "?" || t == "？" -> Command.Help
            // 重置要放在“次数/加”之前（“重置次数”含“次数”）
            t.contains("重置") || t.contains("清零") || t.equals("reset", true) -> Command.ResetUnlocks
            // 加机会要放在“次数/解锁”之前；兼容“加1次/加一次/加次数/加机会”，但“加油”不触发
            (t.contains("加") && (t.contains("次") || t.contains("机会") || t.contains("答题"))) || t.startsWith("+") || t.contains("奖励") -> Command.GrantExtra((num ?: 1).coerceIn(1, 10))
            // 设次数要放在“解锁”之前，避免“解锁次数3”被当成立即解锁
            t.contains("次数") || t.startsWith("限制") || t.startsWith("limit", true) -> Command.SetUnlockLimit((num ?: 3).coerceIn(0, 20))
            t.startsWith("解锁") || t.startsWith("unlock", true) -> Command.UnlockNow((num ?: 30).coerceIn(1, 240))
            t.startsWith("锁定") || t.startsWith("马上锁") || t.startsWith("lock", true) -> Command.LockNow
            t.startsWith("停用") || t.startsWith("暂停") || t.contains("不锁") -> Command.PauseLock((num ?: 60).coerceIn(1, 1440))
            else -> Command.MessageToChild(t)
        }
    }

    // ===== 出站：发文本到群 =====
    fun sendText(text: String): Boolean {
        val tk = token() ?: return false
        val content = JSONObject().put("text", text).toString()
        val body = JSONObject().put("receive_id", CHAT_ID).put("msg_type", "text").put("content", content).toString()
        val resp = httpPost("$BASE/im/v1/messages?receive_id_type=chat_id", body, tk) ?: return false
        return resp.optInt("code", -1) == 0
    }

    // ===== 入站：拉取家长新消息（仅 user 发的、晚于上次处理的），返回文本列表（旧->新）=====
    fun pollNewMessages(context: Context): List<String> {
        val tk = token() ?: return emptyList()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastTs = prefs.getLong(KEY_LAST_TS, 0L)
        val resp = httpGet("$BASE/im/v1/messages?container_id_type=chat&container_id=$CHAT_ID&sort_type=ByCreateTimeDesc&page_size=20", tk)
            ?: return emptyList()
        if (resp.optInt("code", -1) != 0) return emptyList()
        val items = resp.optJSONObject("data")?.optJSONArray("items") ?: return emptyList()

        var maxTs = lastTs
        val fresh = mutableListOf<Pair<Long, String>>()
        for (i in 0 until items.length()) {
            val m = items.getJSONObject(i)
            val ts = m.optString("create_time", "0").toLongOrNull() ?: 0L
            if (ts > maxTs) maxTs = ts
            val senderType = m.optJSONObject("sender")?.optString("sender_type") ?: ""
            if (senderType != "user") continue                 // 忽略机器人自己发的，避免回环
            if (ts <= lastTs) continue
            if (m.optString("msg_type") != "text") continue
            val text = extractText(m.optJSONObject("body")?.optString("content"))
            if (text.isNotBlank()) fresh.add(ts to text)
        }
        prefs.edit().putLong(KEY_LAST_TS, maxTs).apply()
        // 首次运行（lastTs==0）只记录基线，不处理历史消息
        if (lastTs == 0L) return emptyList()
        return fresh.sortedBy { it.first }.map { it.second }
    }

    private fun extractText(content: String?): String {
        if (content.isNullOrBlank()) return ""
        return try {
            JSONObject(content).optString("text", "").replace(Regex("@_\\w+"), "").trim()
        } catch (e: Exception) { "" }
    }

    // ===== token（内存缓存，提前 60s 刷新）=====
    private fun token(): String? {
        if (token.isNotEmpty() && System.currentTimeMillis() < tokenExpireAt - 60_000) return token
        val body = JSONObject().put("app_id", APP_ID).put("app_secret", APP_SECRET).toString()
        val resp = httpPost("$BASE/auth/v3/tenant_access_token/internal", body, null) ?: return null
        if (resp.optInt("code", -1) != 0) return null
        token = resp.optString("tenant_access_token", "")
        tokenExpireAt = System.currentTimeMillis() + resp.optInt("expire", 7200) * 1000L
        return token.ifEmpty { null }
    }

    // ===== HTTP 小工具（沿用 reportToDingTalkRaw 的 HttpURLConnection 模式）=====
    private fun httpPost(url: String, jsonBody: String, bearer: String?): JSONObject? = request("POST", url, jsonBody, bearer)
    private fun httpGet(url: String, bearer: String?): JSONObject? = request("GET", url, null, bearer)

    private fun request(method: String, url: String, jsonBody: String?, bearer: String?): JSONObject? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (bearer != null) conn.setRequestProperty("Authorization", "Bearer $bearer")
            if (jsonBody != null) {
                conn.doOutput = true
                OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(jsonBody) }
            }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: return null
            JSONObject(text)
        } catch (e: Exception) {
            null
        }
    }
}
