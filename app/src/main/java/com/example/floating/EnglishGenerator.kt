package com.example.floating

import kotlin.random.Random

/**
 * 英语启蒙生成器 —— 幼儿/零基础，听力优先。
 * 主体是“听音选图”（听发音→点 emoji 图片，sound→meaning 直连，不经中文翻译）；
 * 新词“先教后测”：第一次出现给教学卡（题干露出单词+图，发音），之后才隐藏答案考。
 * 另保留少量字母大小写/顺序认读（纯视觉，为以后自然拼读做铺垫）。
 * 选词由 QuestionBank 传入的逐词掌握度驱动（学习中的词反复出现、已掌握的少出、新词缓慢引入）。
 */
object EnglishGenerator {

    // 听音选图词库：单词 to emoji（图片），按主题分组，干扰项取同主题 emoji
    private val pictureVocab: List<List<Pair<String, String>>> = listOf(
        // 动物
        listOf("cat" to "🐱", "dog" to "🐶", "pig" to "🐷", "duck" to "🦆", "bird" to "🐦",
            "fish" to "🐟", "rabbit" to "🐰", "panda" to "🐼", "tiger" to "🐯", "monkey" to "🐵"),
        // 水果
        listOf("apple" to "🍎", "banana" to "🍌", "pear" to "🍐", "grape" to "🍇",
            "peach" to "🍑", "watermelon" to "🍉", "strawberry" to "🍓", "orange" to "🍊"),
        // 颜色（用色块 emoji）
        listOf("red" to "🔴", "yellow" to "🟡", "blue" to "🔵", "green" to "🟢",
            "black" to "⚫", "white" to "⚪", "orange" to "🟠", "purple" to "🟣"),
        // 数字（用 keycap emoji）
        listOf("one" to "1️⃣", "two" to "2️⃣", "three" to "3️⃣", "four" to "4️⃣", "five" to "5️⃣",
            "six" to "6️⃣", "seven" to "7️⃣", "eight" to "8️⃣", "nine" to "9️⃣", "ten" to "🔟"),
        // 食物
        listOf("cake" to "🍰", "bread" to "🍞", "milk" to "🥛", "egg" to "🥚",
            "rice" to "🍚", "noodles" to "🍜", "ice cream" to "🍦", "cookie" to "🍪"),
        // 天气
        listOf("sun" to "☀️", "rain" to "🌧️", "snow" to "❄️", "cloud" to "☁️", "rainbow" to "🌈")
    )

    private data class Word(val en: String, val emoji: String, val cat: Int)
    private val allWords: List<Word> by lazy {
        pictureVocab.flatMapIndexed { ci, cat -> cat.map { Word(it.first, it.second, ci) } }
    }

    var lastGeneratedType: String = ""
    var lastWord: String = ""        // 本题对应的英文单词（仅听音选图有，供 QuestionBank 标记“已引入”）

    fun generate(): Question = generateWeighted(emptyMap())

    // mastery: 单词 -> 答对次数（不在表=全新词；0..2=学习中；>=3=已掌握）
    fun generateWeighted(mastery: Map<String, Int>): Question {
        lastWord = ""
        // 听力为主（约 80%），字母认读为辅
        if (Random.nextInt(100) < 80) {
            lastGeneratedType = "english-listenPick"
            return listenAndPick(mastery)
        }
        return if (Random.nextBoolean()) {
            lastGeneratedType = "english-letterCase"; letterCase()
        } else {
            lastGeneratedType = "english-letterOrder"; letterOrder()
        }
    }

    // 听音选图：掌握度加权选词 + 先教后测
    private fun listenAndPick(mastery: Map<String, Int>): Question {
        val weights = allWords.map { w ->
            when (val c = mastery[w.en]) {
                null -> 3        // 全新词：缓慢引入
                in 0..2 -> 5     // 学习中：反复出现直到掌握
                else -> 1        // 已掌握：偶尔复现
            }
        }
        val total = weights.sum()
        var r = Random.nextInt(total)
        var idx = 0
        for (x in weights) { r -= x; if (r < 0) break; idx++ }
        if (idx >= allWords.size) idx = allWords.size - 1

        val target = allWords[idx]
        lastWord = target.en
        val distractors = allWords.filter { it.cat == target.cat && it.emoji != target.emoji }
            .shuffled().take(3).map { it.emoji }
        val options = (distractors + target.emoji).shuffled()

        val isNew = !mastery.containsKey(target.en)   // 先教后测：新词露出答案，旧词隐藏
        val text = if (isNew) "🆕 这是 ${target.emoji} ${target.en}，听一听，点一下它"
        else "🔊 听一听，点出听到的图片"
        return Question(text, options, options.indexOf(target.emoji), audioWord = target.en)
    }

    // 字母大小写
    private fun letterCase(): Question {
        val i = Random.nextInt(26)
        val upper = ('A' + i).toString()
        val lower = ('a' + i).toString()
        return if (Random.nextBoolean()) {
            val wrongs = (0 until 26).filter { it != i }.shuffled().take(3).map { ('a' + it).toString() }
            createQuestion("大写字母 “$upper” 的小写是？", lower, wrongs)
        } else {
            val wrongs = (0 until 26).filter { it != i }.shuffled().take(3).map { ('A' + it).toString() }
            createQuestion("小写字母 “$lower” 的大写是？", upper, wrongs)
        }
    }

    // 字母顺序
    private fun letterOrder(): Question {
        return if (Random.nextBoolean()) {
            val i = Random.nextInt(25)          // A..Y，问后一个
            val cur = ('A' + i).toString()
            val ans = ('A' + i + 1).toString()
            val wrongs = listOf(('A' + i - 1).coerceAtLeast('A'), 'A' + (i + 2).coerceAtMost(25), 'A' + (i + 3) % 26)
                .map { it.toString() }.filter { it != ans }.distinct()
            createQuestion("英文字母表中，字母 “$cur” 后面是哪个？", ans, wrongs)
        } else {
            val i = Random.nextInt(1, 26)        // B..Z，问前一个
            val cur = ('A' + i).toString()
            val ans = ('A' + i - 1).toString()
            val wrongs = listOf('A' + (i + 1).coerceAtMost(25), 'A' + (i - 2).coerceAtLeast(0), 'A' + (i + 2) % 26)
                .map { it.toString() }.filter { it != ans }.distinct()
            createQuestion("英文字母表中，字母 “$cur” 前面是哪个？", ans, wrongs)
        }
    }

    private fun createQuestion(text: String, correct: String, wrongs: List<String>): Question {
        val uniqueWrongs = wrongs.filter { it != correct }.distinct().take(3).toMutableList()
        val filler = listOf("A", "B", "C", "D", "E", "F")
        while (uniqueWrongs.size < 2) {
            val f = filler.random()
            if (f != correct && !uniqueWrongs.contains(f)) uniqueWrongs.add(f)
        }
        val allOptions = (uniqueWrongs + correct).shuffled()
        return Question(text, allOptions, allOptions.indexOf(correct))
    }
}
