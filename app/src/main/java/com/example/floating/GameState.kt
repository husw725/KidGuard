package com.example.floating

import android.content.Context
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.text.style.ImageSpan
import android.text.style.RelativeSizeSpan
import android.view.View
import kotlin.random.Random

/**
 * 游戏化状态（纯函数）：宝箱 / 称号（状元路）/ 宠物 / 图鉴。
 * 星星是唯一货币（QuestionBank 的 KEY_STARS）：连击/BOSS/宝箱多赚星星，
 * 星星驱动称号与宠物成长；图鉴从现有掌握度数据派生，零额外存储。
 */
object GameState {

    // 3D 立体美术（微软 Fluent emoji，MIT，见 THIRD_PARTY_LICENSES.md）：emoji → drawable
    // 注意 🐤 是侧面 Baby chick，🐥 是正面 Front-facing baby chick
    private val art = mapOf(
        "🥚" to R.drawable.art_egg, "🐣" to R.drawable.art_hatching_chick,
        "🐤" to R.drawable.art_baby_chick, "🐥" to R.drawable.art_chick_front,
        "🐔" to R.drawable.art_chicken, "🐦‍🔥" to R.drawable.art_phoenix, "🎁" to R.drawable.art_gift,
        "🐰" to R.drawable.art_rabbit, "🐱" to R.drawable.art_cat, "🐶" to R.drawable.art_dog,
        "🐹" to R.drawable.art_hamster, "🐷" to R.drawable.art_pig, "🐸" to R.drawable.art_frog,
        "🐟" to R.drawable.art_fish, "🐼" to R.drawable.art_panda, "🦊" to R.drawable.art_fox,
        "🐧" to R.drawable.art_penguin, "🦉" to R.drawable.art_owl, "🐢" to R.drawable.art_turtle,
        "🦋" to R.drawable.art_butterfly, "🦄" to R.drawable.art_unicorn, "🐬" to R.drawable.art_dolphin,
        "🦚" to R.drawable.art_peacock, "🐉" to R.drawable.art_dragon
    )

    // 单个 3D 图标（可灰度=未收集/未达成）；没有对应美术时回退原 emoji 文本
    fun icon(context: Context, emoji: String, sizeDp: Int, grey: Boolean = false): CharSequence {
        val resId = art[emoji] ?: return emoji
        val d = context.resources.getDrawable(resId, context.theme).mutate()
        val px = (sizeDp * context.resources.displayMetrics.density).toInt()
        d.setBounds(0, 0, px, px)
        if (grey) {
            d.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
            d.alpha = 120
        }
        return SpannableString("￼").apply {
            setSpan(ImageSpan(d, ImageSpan.ALIGN_BOTTOM), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

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

    private const val KEY_PENDING_BOXES = "PendingBoxes"   // 攒满但还没点开的盒子数

    // 答对一题调用：+1 盲盒能量；攒满时出一个待开的盒子（点击才开），返回是否刚攒满
    fun addBoxProgress(context: Context): Boolean {
        val p = context.getSharedPreferences(PET_PREFS, Context.MODE_PRIVATE)
        val prog = p.getInt(KEY_HATCH_PROGRESS, 0) + 1
        if (prog < HATCH_TARGET) {
            p.edit().putInt(KEY_HATCH_PROGRESS, prog).apply()
            return false
        }
        p.edit()
            .putInt(KEY_HATCH_PROGRESS, 0)
            .putInt(KEY_PENDING_BOXES, p.getInt(KEY_PENDING_BOXES, 0) + 1)
            .apply()
        return true
    }

    fun hasPendingBox(context: Context): Boolean =
        context.getSharedPreferences(PET_PREFS, Context.MODE_PRIVATE).getInt(KEY_PENDING_BOXES, 0) > 0

    // 点开盒子：抽稀有度与小动物，入农场、分钟入账；返回（动物emoji, 揭晓文案, 奖励分钟）
    fun openBox(context: Context): Triple<String, String, Int> {
        val p = context.getSharedPreferences(PET_PREFS, Context.MODE_PRIVATE)
        // 手气加成：挪出的 L 点按 60/30/10 分给稀有/史诗/隐藏款（整除余数归稀有）
        val luck = luckFor(QuestionBank.getStars(context))
        val common = 60 - luck
        val epic = 9 + luck * 3 / 10
        val legend = 1 + luck / 10
        val rare = 100 - common - epic - legend
        val r = Random.nextInt(100)
        val tier = when {
            r < common -> tiers[0]
            r < common + rare -> tiers[1]
            r < common + rare + epic -> tiers[2]
            else -> tiers[3]
        }
        val (emoji, name) = tier.animals.random()
        val farm = p.getString(KEY_FARM_ANIMALS, "") ?: ""
        p.edit()
            .putInt(KEY_PENDING_BOXES, maxOf(0, p.getInt(KEY_PENDING_BOXES, 0) - 1))
            .putString(KEY_FARM_ANIMALS, if (farm.isEmpty()) emoji else "$farm,$emoji")
            .putInt(KEY_PENDING_HATCH_MIN, p.getInt(KEY_PENDING_HATCH_MIN, 0) + tier.minutes)
            .apply()
        val title = when (tier.label) {
            "普通" -> "🎉 是 $name！"
            "稀有" -> "✨ 哇！稀有款 $name！"
            "史诗" -> "💫💫 史诗款 $name！！"
            else -> "🌈🌈🌈 隐藏款 $name！！！"
        }
        return Triple(emoji, title, tier.minutes)
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
        val phoenix = phoenixCount(stars).let { if (it > 0) "　🐦‍🔥×$it" else "" }
        return "${petFor(stars).first} ⭐$stars$phoenix　🏡×$farmCount　🎁 盲盒 $bar $prog/$HATCH_TARGET"
    }

    // 农场图鉴：按稀有度分区展示全部动物，已收集彩色+数量，未收集灰度剪影
    fun farmText(context: Context): CharSequence {
        val p = context.getSharedPreferences(PET_PREFS, Context.MODE_PRIVATE)
        val counts = farmList(p).groupingBy { it }.eachCount()
        val sb = SpannableStringBuilder("🏡 我的农场图鉴\n")
        for (t in tiers) {
            val name = if (t.label == "传说") "隐藏款 🌈" else when (t.label) {
                "稀有" -> "稀有 ✨"; "史诗" -> "史诗 💫"; else -> "普通"
            }
            appendSmall(sb, "\n$name · 每只 +${t.minutes} 分钟\n")
            for ((emoji, _) in t.animals) {
                val n = counts[emoji] ?: 0
                sb.append(icon(context, emoji, 40, grey = n == 0))
                if (n > 1) appendSmall(sb, "×$n")
                sb.append("  ")
            }
            sb.append("\n")
        }
        val total = counts.values.sum()
        sb.append("\n已收集 ${counts.keys.size}/${tiers.sumOf { it.animals.size }} 种 · 共 $total 只")
        if (total == 0) sb.append("\n答对题攒满盲盒能量，就能开出神秘小伙伴哦！")
        return sb
    }

    // 小鸡进化链（速览页顶部）：已达成彩色、未来灰度，当前阶段更大；
    // 传 onStage 时每个图标可点（配合 LinkMovementMethod），点开看该阶段详情
    fun petEvolution(context: Context, onStage: ((Int) -> Unit)? = null): CharSequence {
        val stars = QuestionBank.getStars(context)
        val s = stars % CYCLE
        val n = phoenixCount(stars)
        val idx = pets.indexOfLast { s >= it.first }
        val sb = SpannableStringBuilder(if (n > 0) "🐦‍🔥 我的小鸡（已养成凤凰 ×$n）\n\n" else "🐔 我的小鸡\n\n")
        pets.forEachIndexed { i, (_, emoji) ->
            val start = sb.length
            sb.append(icon(context, emoji, if (i == idx) 56 else 38, grey = i > idx))
            if (onStage != null) sb.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) { onStage(i) }
                override fun updateDrawState(ds: TextPaint) {}   // 图标不需要链接样式
            }, start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (i < pets.size - 1) appendSmall(sb, " ➜ ")
        }
        sb.append("\n")
        appendSmall(sb, if (idx == pets.size - 1) "⭐$stars · 第 ${n + 1} 只凤凰养成！再攒 ${CYCLE - s}⭐ 迎接新蛋"
            else "⭐$stars · 再攒 ${pets[idx + 1].first - s}⭐ 进化成下一形态")
        return sb
    }

    private fun appendSmall(sb: SpannableStringBuilder, text: String) {
        val start = sb.length
        sb.append(text)
        sb.setSpan(RelativeSizeSpan(0.75f), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun farmList(p: android.content.SharedPreferences): List<String> {
        val s = p.getString(KEY_FARM_ANIMALS, "") ?: ""
        return if (s.isEmpty()) emptyList() else s.split(",")
    }

    // 宠物成长（轮回制）：每 260⭐ 一轮——240 养成传说凤凰，欣赏 20⭐ 后自动迎来下一只蛋。
    // 已养成凤凰数 = 总星数 / 260，纯派生零存储，重装恢复天然兼容。
    private const val CYCLE = 260
    private val pets = listOf(0 to "🥚", 15 to "🐣", 40 to "🐥", 80 to "🐤", 140 to "🐔", 240 to "🐦‍🔥")

    fun phoenixCount(stars: Int): Int = stars / CYCLE

    private val stageNames = listOf("蛋蛋", "破壳", "毛毛球", "小鸡", "大公鸡", "传说凤凰")
    private val stageLuck = listOf(0, 3, 6, 9, 12, 15)   // 各阶段的开盒手气（百分点）

    private fun stageIdx(stars: Int): Int = pets.indexOfLast { stars % CYCLE >= it.first }

    // 开盒手气：鸡的阶段 + 每只已养成凤凰 +3，封顶 +30（从"普通60%"里挪给稀有以上）
    fun luckFor(stars: Int): Int =
        minOf(30, stageLuck[stageIdx(stars)] + 3 * phoenixCount(stars))

    // 进化链上第 i 阶的详情（点图标查看）：所需星星 + 该阶段手气
    fun stageDetail(context: Context, i: Int): String {
        val s = QuestionBank.getStars(context) % CYCLE
        val (need, emoji) = pets[i]
        val luckText = "开盒手气 +${stageLuck[i]}%"
        return if (s >= need) "✅ ${stageNames[i]} $emoji 已达成 · $luckText"
        else "${stageNames[i]} $emoji：需 ${need}⭐（还差 ${need - s}⭐）· $luckText"
    }

    fun petFor(stars: Int): Pair<String, String> {
        val s = stars % CYCLE
        val idx = pets.indexOfLast { s >= it.first }
        val emoji = pets[idx].second
        // 进度文案都用"再攒 X⭐"的差值口径，第二轮起总星数和轮内进度不同，绝对值会看糊涂
        return if (idx == pets.size - 1)
            emoji to "$emoji 第 ${stars / CYCLE + 1} 只传说凤凰养成！再攒 ${CYCLE - s}⭐ 迎接新蛋"
        else emoji to "$emoji 再攒 ${pets[idx + 1].first - s}⭐ 就会变成 ${pets[idx + 1].second}"
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
