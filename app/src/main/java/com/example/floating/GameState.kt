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
