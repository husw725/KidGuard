package com.example.floating

import kotlin.random.Random

object OlympiadMathGenerator {
    /**
     * 生成一道奥数风格的题目
     */
    fun generate(): Question {
        return when (Random.nextInt(3)) {
            0 -> generatePeriodicity() // 周期律
            1 -> generateSequencePattern() // 数列规律
            else -> generateAlgebraicReasoning() // 简单代数推理
        }
    }
    // 加权版本
    var lastGeneratedType: String = ""
    private val olympiadTypeNames = listOf("periodicity", "sequencePattern", "algebraicReasoning")

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
        return when (idx) {
            0 -> generatePeriodicity()
            1 -> generateSequencePattern()
            else -> generateAlgebraicReasoning()
        }
    }

    private fun createQuestion(text: String, correct: String, wrongs: List<String>): Question {
        val uniqueWrongs = wrongs.filter { it != correct }.distinct().toMutableList()
        val filler = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10")
        while (uniqueWrongs.size < 3) {
            val f = filler.random()
            if (f != correct && !uniqueWrongs.contains(f)) uniqueWrongs.add(f)
        }
        val allOptions = (uniqueWrongs.take(3) + correct).shuffled()
        return Question(text, allOptions, allOptions.indexOf(correct))
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
            seq.filter { it != ans }
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
            listOf("${ans - diff}", "${ans + diff}", "${ans + 1}")
        )
    }

    private fun generateAlgebraicReasoning(): Question {
        val x = Random.nextInt(2, 6)
        val y = Random.nextInt(1, 4)
        val sum = x + y
        val diff = x - y
        
        return createQuestion(
            "已知 A + B = $sum，A - B = $diff，那么 A 是多少？",
            "$x",
            listOf("$y", "${x + 1}", "${x - 1}")
        )
    }
}
