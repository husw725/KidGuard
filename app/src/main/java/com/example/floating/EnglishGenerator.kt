package com.example.floating

import kotlin.random.Random

/**
 * 英语启蒙生成器 — 对标人教版 PEP 三年级起点
 * 纯文字选择题（无发音/图片）：中英互译、字母大小写与顺序、情景对话、单词归类。
 * 干扰项尽量取同类词，保证有迷惑性又适合启蒙。
 */
object EnglishGenerator {

    // ===== 分类词库（英文 to 中文）=====
    private val animals = listOf(
        "cat" to "猫", "dog" to "狗", "pig" to "猪", "duck" to "鸭子", "bird" to "鸟",
        "fish" to "鱼", "rabbit" to "兔子", "panda" to "熊猫", "tiger" to "老虎", "monkey" to "猴子"
    )
    private val colors = listOf(
        "red" to "红色", "yellow" to "黄色", "blue" to "蓝色", "green" to "绿色",
        "black" to "黑色", "white" to "白色", "orange" to "橙色", "pink" to "粉色"
    )
    private val fruits = listOf(
        "apple" to "苹果", "banana" to "香蕉", "pear" to "梨", "grape" to "葡萄",
        "peach" to "桃子", "watermelon" to "西瓜"
    )
    private val school = listOf(
        "pen" to "钢笔", "pencil" to "铅笔", "book" to "书", "bag" to "书包",
        "ruler" to "尺子", "desk" to "书桌", "chair" to "椅子"
    )
    private val family = listOf(
        "father" to "爸爸", "mother" to "妈妈", "brother" to "哥哥", "sister" to "姐姐",
        "grandpa" to "爷爷", "grandma" to "奶奶"
    )
    private val numbers = listOf(
        "one" to "1", "two" to "2", "three" to "3", "four" to "4", "five" to "5",
        "six" to "6", "seven" to "7", "eight" to "8", "nine" to "9", "ten" to "10"
    )

    private val allCategories = listOf(animals, colors, fruits, school, family)

    val typeNames = listOf("zh2en", "en2zh", "letterCase", "letterOrder", "number", "dialogue", "classify")
    var lastGeneratedType: String = ""

    fun generate(): Question = dispatch(Random.nextInt(typeNames.size))

    fun generateWeighted(seenRound: Map<String, Int>, errors: Map<String, Int>): Question {
        val maxRound = (seenRound.values.maxOrNull() ?: 0)
        val weights = typeNames.map { tn ->
            val key = "english-$tn"
            val lastSeen = seenRound[key] ?: 0
            val missed = if (lastSeen > 0) maxRound - lastSeen else maxRound + 1
            val e = errors[key] ?: 0
            missed * 2 + e * 5 + 1
        }
        val total = weights.sum()
        var r = Random.nextInt(total)
        var idx = 0
        for (w in weights) { r -= w; if (r < 0) break; idx++ }
        if (idx >= typeNames.size) idx = typeNames.size - 1
        return dispatch(idx)
    }

    private fun dispatch(idx: Int): Question {
        lastGeneratedType = "english-${typeNames[idx]}"
        return when (idx) {
            0 -> zh2en()
            1 -> en2zh()
            2 -> letterCase()
            3 -> letterOrder()
            4 -> number()
            5 -> dialogue()
            else -> classify()
        }
    }

    // ① 看中文选英文
    private fun zh2en(): Question {
        val cat = allCategories.random()
        val (en, zh) = cat.random()
        val wrongs = cat.filter { it.first != en }.shuffled().take(3).map { it.first }
        return createQuestion("“$zh”用英语怎么说？", en, wrongs)
    }

    // ② 看英文选中文
    private fun en2zh(): Question {
        val cat = allCategories.random()
        val (en, zh) = cat.random()
        val wrongs = cat.filter { it.second != zh }.shuffled().take(3).map { it.second }
        return createQuestion("单词 “$en” 的意思是？", zh, wrongs)
    }

    // ③ 字母大小写
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

    // ④ 字母顺序
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

    // ⑤ 数字英文
    private fun number(): Question {
        val (en, num) = numbers.random()
        return if (Random.nextBoolean()) {
            val wrongs = numbers.filter { it.first != en }.shuffled().take(3).map { it.first }
            createQuestion("数字 “$num” 的英语是？", en, wrongs)
        } else {
            val wrongs = numbers.filter { it.second != num }.shuffled().take(3).map { it.second }
            createQuestion("单词 “$en” 表示数字几？", num, wrongs)
        }
    }

    // ⑥ 情景对话
    private fun dialogue(): Question {
        val items = listOf(
            Triple("早上见到老师，应该说：", "Good morning!", listOf("Good night!", "Goodbye!", "Thank you!")),
            Triple("别人帮助了你，要表示感谢，说：", "Thank you!", listOf("Sorry!", "Hello!", "Goodbye!")),
            Triple("和同学说“再见”，用英语是：", "Goodbye!", listOf("Hello!", "Good morning!", "Thank you!")),
            Triple("见面打招呼“你好”，用英语是：", "Hello!", listOf("Bye!", "Sorry!", "Good night!")),
            Triple("不小心踩到别人，应该说：", "Sorry!", listOf("Thank you!", "Hello!", "Good morning!")),
            Triple("晚上睡觉前，对妈妈说：", "Good night!", listOf("Good morning!", "Thank you!", "Hello!")),
            Triple("老师说“Sit down.”是让你：", "坐下", listOf("起立", "看书", "安静")),
            Triple("老师说“Stand up.”是让你：", "起立", listOf("坐下", "看书", "举手"))
        ).random()
        return createQuestion(items.first, items.second, items.third)
    }

    // ⑦ 单词归类
    private fun classify(): Question {
        val items = listOf(
            Triple("下面哪个是动物？", animals.random().first, listOf(colors.random().first, fruits.random().first, school.random().first)),
            Triple("下面哪个是颜色？", colors.random().first, listOf(animals.random().first, fruits.random().first, numbers.random().first)),
            Triple("下面哪个是水果？", fruits.random().first, listOf(animals.random().first, colors.random().first, school.random().first)),
            Triple("下面哪个是文具？", school.random().first, listOf(animals.random().first, colors.random().first, fruits.random().first)),
            Triple("下面哪个表示数字？", numbers.random().first, listOf(animals.random().first, colors.random().first, fruits.random().first))
        ).random()
        return createQuestion(items.first, items.second, items.third)
    }

    private fun createQuestion(text: String, correct: String, wrongs: List<String>): Question {
        val uniqueWrongs = wrongs.filter { it != correct }.distinct().take(3).toMutableList()
        // 兜底：极少数情况下同类词不足，用字母补足，保证至少 3 个选项
        val filler = listOf("A", "B", "C", "D", "E", "F")
        while (uniqueWrongs.size < 2) {
            val f = filler.random()
            if (f != correct && !uniqueWrongs.contains(f)) uniqueWrongs.add(f)
        }
        val allOptions = (uniqueWrongs + correct).shuffled()
        return Question(text, allOptions, allOptions.indexOf(correct))
    }
}
