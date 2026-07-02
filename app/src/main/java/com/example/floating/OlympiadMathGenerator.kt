package com.example.floating

import kotlin.random.Random

object OlympiadMathGenerator {
    /**
     * 生成一道奥数风格的题目
     */
    fun generate(): Question {
        return if (Random.nextBoolean()) generatePeriodicity() else generateSequencePattern()
    }
    // 加权版本
    var lastGeneratedType: String = ""
    private val olympiadTypeNames = listOf("periodicity", "sequencePattern")

    fun generateWeighted(seenRound: Map<String, Int>, errors: Map<String, Int>): Question {
        val maxRound = (seenRound.values.maxOrNull() ?: 0)
        val weights = olympiadTypeNames.map { tn ->
            val key = "olympiad-$tn"
            val lastSeen = seenRound[key] ?: 0
            val missed = if (lastSeen > 0) maxRound - lastSeen else maxRound + 1
            val e = errors[key] ?: 0
            missed * 2 + e * 5 + 1
        }
        val total = weights.sum()
        var r = Random.nextInt(total)
        var idx = 0
        for (w in weights) {
            r -= w
            if (r < 0) break
            idx++
        }
        lastGeneratedType = "olympiad-${olympiadTypeNames[idx]}"
        return if (idx == 0) generatePeriodicity() else generateSequencePattern()
    }

    private fun createQuestion(text: String, correct: String, wrongs: List<String>, tip: String? = null): Question {
        val uniqueWrongs = wrongs.filter { it != correct }.distinct().toMutableList()
        // 根据正确答案类型选择兜底：数字题用数字，非数字题用通用人名
        val filler = if (correct.matches(Regex("\\d+")))
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10")
        else
            listOf("小红", "小华", "小刚", "小丽", "小强", "小芳", "小杰", "小兰")
        while (uniqueWrongs.size < 3) {
            val f = filler.random()
            if (f != correct && !uniqueWrongs.contains(f)) uniqueWrongs.add(f)
        }
        val allOptions = (uniqueWrongs.take(3) + correct).shuffled()
        return Question(text, allOptions, allOptions.indexOf(correct), tip = tip)
    }

    private fun generatePeriodicity(): Question {
        val patterns = listOf(
            Pair("红、黄、蓝、绿", 4),
            Pair("加、减、乘、除", 4),
            Pair("小欣、小明、老师", 3)
        )
        val p = patterns.random()
        val seq = p.first.split("、")
        val n = p.second
        val target = Random.nextInt(10, 30)
        val ans = seq[(target - 1) % n]
        
        return createQuestion(
            "按照${p.first}...的规律排列，第 ${target} 个是什么？",
            ans,
            seq.filter { it != ans },
            "每 $n 个一组循环，看第 ${target} 个落在一组里的第几位"
        )
    }

    private fun generateSequencePattern(): Question {
        val start = Random.nextInt(1, 6)
        val diff = Random.nextInt(1, 6)
        val index = Random.nextInt(3, 8)
        val ans = start + (index - 1) * diff
        val sequence = (1..4).map { start + (it - 1) * diff }.joinToString(", ")
        
        return createQuestion(
            "数列 $sequence, ... 第 $index 项是多少？",
            "$ans",
            listOf("${ans - diff}", "${ans + diff}", "${ans + 1}"),
            "先看每相邻两项相差多少，再从已知的一项往后一步步加"
        )
    }
}
