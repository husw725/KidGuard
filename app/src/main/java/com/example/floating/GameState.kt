package com.example.floating

import android.content.Context
import kotlin.random.Random

/**
 * 游戏化状态（纯函数）：宝箱 / 称号（状元路）/ 宠物 / 图鉴。
 * 星星是唯一货币（QuestionBank 的 KEY_STARS）：连击/BOSS/宝箱多赚星星，
 * 星星驱动称号与宠物成长；图鉴从现有掌握度数据派生，零额外存储。
 */
object GameState {

    // 通关宝箱：随机奖励，返回（展示文案, 额外解锁分钟, 额外星星）
    fun openChest(): Triple<String, Int, Int> {
        val r = Random.nextInt(100)
        return when {
            r < 40 -> Triple("🎁 宝箱开出：额外 +3 分钟！", 3, 0)
            r < 70 -> Triple("🎁 宝箱开出：+1 ⭐！", 0, 1)
            r < 90 -> Triple("🎁 宝箱开出：+2 ⭐！！", 0, 2)
            else -> Triple("🎁 宝箱开出：🌈 幸运彩虹！好运一整天～", 0, 1)
        }
    }

    // ===== 盲盒农场：答对攒盲盒能量，满 15 开出随机小动物（稀有度决定奖励分钟）=====
    // 注：prefs 键沿用最初"孵蛋"命名（HatchProgress 等），保留孩子已攒的进度
    private const val PET_PREFS = "PetPrefs"
    private const val KEY_HATCH_PROGRESS = "HatchProgress"
    private const val KEY_FARM_ANIMALS = "FarmAnimals"        // 逗号分隔的 emoji 序列
    private const val KEY_PENDING_HATCH_MIN = "PendingHatchMinutes"
    private const val HATCH_TARGET = 15

    private data class Tier(val label: String, val minutes: Int, val animals: List<Pair<String, String>>)

    private val tiers = listOf(          // 概率区间在 hatch() 里：60 / 30 / 9 / 1
        Tier("普通", 3, listOf("🐤" to "小鸡", "🐰" to "小兔子", "🐱" to "小猫", "🐶" to "小狗",
            "🐹" to "小仓鼠", "🐷" to "小猪", "🐸" to "小青蛙", "🐟" to "小鱼")),
        Tier("稀有", 5, listOf("🐼" to "熊猫", "🦊" to "小狐狸", "🐧" to "企鹅",
            "🦉" to "猫头鹰", "🐢" to "小乌龟", "🦋" to "蝴蝶")),
        Tier("史诗", 10, listOf("🦄" to "独角兽", "🐬" to "海豚", "🦚" to "孔雀")),
        Tier("传说", 20, listOf("🐉" to "神龙"))
    )

    // 答对一题调用：+1 盲盒能量；攒满开盒时返回庆祝文案（含分钟奖励），否则 null
    fun addBoxProgress(context: Context): String? {
        val p = context.getSharedPreferences(PET_PREFS, Context.MODE_PRIVATE)
        val prog = p.getInt(KEY_HATCH_PROGRESS, 0) + 1
        if (prog < HATCH_TARGET) {
            p.edit().putInt(KEY_HATCH_PROGRESS, prog).apply()
            return null
        }
        // 开盲盒！按稀有度抽卡
        val r = Random.nextInt(100)
        val tier = when { r < 60 -> tiers[0]; r < 90 -> tiers[1]; r < 99 -> tiers[2]; else -> tiers[3] }
        val (emoji, name) = tier.animals.random()
        val farm = p.getString(KEY_FARM_ANIMALS, "") ?: ""
        p.edit()
            .putInt(KEY_HATCH_PROGRESS, 0)
            .putString(KEY_FARM_ANIMALS, if (farm.isEmpty()) emoji else "$farm,$emoji")
            .putInt(KEY_PENDING_HATCH_MIN, p.getInt(KEY_PENDING_HATCH_MIN, 0) + tier.minutes)
            .apply()
        return when (tier.label) {
            "普通" -> "🎁 盲盒打开了……是 $emoji $name！+${tier.minutes} 分钟"
            "稀有" -> "✨ 哇！稀有款 $emoji $name！+${tier.minutes} 分钟"
            "史诗" -> "💫💫 史诗款 $emoji $name！！+${tier.minutes} 分钟"
            else -> "🌈🌈🌈 隐藏款 $emoji $name！！！+${tier.minutes} 分钟"
        }
    }

    // 通关时取出攒下的盲盒奖励分钟并清零
    fun redeemBoxMinutes(context: Context): Int {
        val p = context.getSharedPreferences(PET_PREFS, Context.MODE_PRIVATE)
        val m = p.getInt(KEY_PENDING_HATCH_MIN, 0)
        if (m > 0) p.edit().putInt(KEY_PENDING_HATCH_MIN, 0).apply()
        return m
    }

    // 答题页顶部状态行：鸡妈妈 + 星星 + 农场数 + 孵化进度条
    fun statusLine(context: Context): String {
        val p = context.getSharedPreferences(PET_PREFS, Context.MODE_PRIVATE)
        val prog = p.getInt(KEY_HATCH_PROGRESS, 0)
        val farmCount = farmList(p).size
        val stars = QuestionBank.getStars(context)
        val filled = prog * 5 / HATCH_TARGET
        val bar = "▓".repeat(filled) + "░".repeat(5 - filled)
        return "${petFor(stars).first} ⭐$stars　🏡×$farmCount　🎁 盲盒 $bar $prog/$HATCH_TARGET"
    }

    // 农场页：emoji 墙 + 收集统计
    fun farmText(context: Context): String {
        val p = context.getSharedPreferences(PET_PREFS, Context.MODE_PRIVATE)
        val animals = farmList(p)
        if (animals.isEmpty()) return "🏡 我的农场\n\n还空着呢～答对题攒满盲盒能量，\n就能开出神秘小伙伴哦！"
        val byTier = tiers.map { t -> t to animals.count { a -> t.animals.any { it.first == a } } }
        val stats = byTier.filter { it.second > 0 && it.first.label != "普通" }
            .joinToString("　") { "${it.first.label} x${it.second}" }
        return "🏡 我的农场\n\n${animals.joinToString(" ")}\n\n已收集 ${animals.size} 只小伙伴" +
            if (stats.isNotEmpty()) "\n$stats" else ""
    }

    private fun farmList(p: android.content.SharedPreferences): List<String> {
        val s = p.getString(KEY_FARM_ANIMALS, "") ?: ""
        return if (s.isEmpty()) emptyList() else s.split(",")
    }

    // 宠物成长：星星总数 -> (emoji, 阶段文案)
    private val pets = listOf(0 to "🥚", 15 to "🐣", 40 to "🐥", 80 to "🐤", 140 to "🐔")

    fun petFor(stars: Int): Pair<String, String> {
        val idx = pets.indexOfLast { stars >= it.first }
        val emoji = pets[idx].second
        return if (idx == pets.size - 1) emoji to "$emoji 你的小鸡完全长大啦！"
        else emoji to "$emoji 攒到 ${pets[idx + 1].first}⭐ 就会变成 ${pets[idx + 1].second}"
    }

    // 图鉴：英语 emoji 墙 + 已点亮的必背内容
    fun galleryText(context: Context): String {
        val masteredEn = QuestionBank.getMasteredEnglish(context)
        val vocab = EnglishGenerator.vocabulary
        val totalWords = vocab.map { it.first }.distinct().size
        val emojiWall = vocab.filter { it.first in masteredEn }.map { it.second }.distinct()
        val enLine = if (emojiWall.isEmpty()) "还没点亮，答对同一个单词 3 次就能点亮它"
            else emojiWall.joinToString(" ")

        val masteredRe = QuestionBank.getMasteredRecitation(context)
        val reNames = masteredRe.map { k ->
            when {
                k.startsWith("poem-") -> "《${k.removePrefix("poem-")}》"
                k.startsWith("saying-") -> "「${k.removePrefix("saying-")}…」"
                else -> "《${k.removePrefix("passage-")}》"
            }
        }.sorted()
        val reLine = if (reNames.isEmpty()) "还没点亮，答对同一首诗 3 次就能点亮它" else reNames.joinToString("、")

        return "📖 我的图鉴\n\n🔤 英语单词（${masteredEn.size}/$totalWords）\n$enLine\n\n📜 三上必背（${masteredRe.size}/${Grade3Recitation.totalItems}）\n$reLine"
    }
}
