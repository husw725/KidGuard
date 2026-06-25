package com.example.floating

import kotlin.random.Random

/**
 * 巧算 / 简便计算生成器 —— 重在“教方法”，不只是算得数。
 * 两种思想：
 *   ① 凑整法：198 + 66 → 200 + 66 - 2（接近整百的数看成整百，再调整）
 *   ② 加减混合搬家凑整：85 - 33 + 15 → 85 + 15 - 33（把能凑整的两数挪到一起先算）
 * 三种题型：选简便方法、按方法算结果、填调整量。
 * 干扰项都对应孩子最常犯的错（忘调整 / 调整方向反 / 误当成减去和）。
 */
object SmartCalcGenerator {

    val typeNames = listOf("round_select", "round_compute", "round_fill", "reorder_select", "reorder_compute")
    var lastGeneratedType: String = ""

    fun generate(): Question = dispatch(Random.nextInt(typeNames.size))

    fun generateWeighted(seenRound: Map<String, Int>, errors: Map<String, Int>): Question {
        val maxRound = seenRound.values.maxOrNull() ?: 0
        val weights = typeNames.map { tn ->
            val key = "smartcalc-$tn"
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
        lastGeneratedType = "smartcalc-${typeNames[idx]}"
        return when (idx) {
            0 -> roundSelect()
            1 -> roundCompute()
            2 -> roundFill()
            3 -> reorderSelect()
            else -> reorderCompute()
        }
    }

    // ============ 凑整法 ============
    // a op nr，其中 nr 接近整百 R（nr = R ± diff）
    private data class RoundSetup(val a: Int, val nr: Int, val r: Int, val diff: Int, val op: Char, val below: Boolean)

    private fun roundSetup(): RoundSetup {
        val r = listOf(100, 200, 300).random()
        val diff = Random.nextInt(1, 4)          // 1~3
        val below = Random.nextBoolean()
        val nr = if (below) r - diff else r + diff
        val op = if (Random.nextBoolean()) '+' else '-'
        val a = if (op == '+') Random.nextInt(20, 400) else nr + Random.nextInt(20, 300)
        return RoundSetup(a, nr, r, diff, op, below)
    }

    // 凑整后正确的调整符号
    private fun adjustSign(s: RoundSetup): Char =
        if (s.op == '+') (if (s.below) '-' else '+') else (if (s.below) '+' else '-')

    private fun roundValue(s: RoundSetup): Int = if (s.op == '+') s.a + s.nr else s.a - s.nr

    // ① 选简便方法
    private fun roundSelect(): Question {
        val s = roundSetup()
        val sign = adjustSign(s)
        val wrongSign = if (sign == '+') '-' else '+'
        val correct = "${s.a} ${s.op} ${s.r} $sign ${s.diff}"
        val wrongs = listOf(
            "${s.a} ${s.op} ${s.r} $wrongSign ${s.diff}",   // 调整方向反了
            "${s.a} ${s.op} ${s.r}",                         // 忘了调整
            "${s.a} ${s.op} ${s.r} $sign ${s.diff * 10}"     // 调整量多了 10 倍
        )
        return choice("${s.a} ${s.op} ${s.nr} 怎样算又快又对？", correct, wrongs,
            "把 ${s.nr} 看成 ${s.r}，正确算法是 $correct")
    }

    // ② 按方法算结果
    private fun roundCompute(): Question {
        val s = roundSetup()
        val ans = roundValue(s)
        val sign = adjustSign(s)
        val text = "用凑整法算：${s.a} ${s.op} ${s.nr}（把 ${s.nr} 看成 ${s.r}）= ( )"
        return numChoice(text, ans, listOf(ans + s.diff, ans - s.diff, ans + 2 * s.diff),
            "${s.a} ${s.op} ${s.nr} = ${s.a} ${s.op} ${s.r} $sign ${s.diff} = $ans")
    }

    // ③ 填调整量
    private fun roundFill(): Question {
        val s = roundSetup()
        val sign = adjustSign(s)
        val text = "${s.a} ${s.op} ${s.nr} = ${s.a} ${s.op} ${s.r} $sign (  )"
        return numChoice(text, s.diff, listOf(s.diff + 1, s.diff + 2, s.diff * 10),
            "${s.nr} 与 ${s.r} 相差 ${s.diff}，所以填 ${s.diff}")
    }

    // ============ 加减混合搬家凑整 ============
    // a - b + c，其中 a + c = T（整十/整百），简便算法 a + c - b = T - b
    private data class ReorderSetup(val a: Int, val b: Int, val c: Int, val t: Int)

    private fun reorderSetup(): ReorderSetup {
        val t = listOf(60, 70, 80, 90, 100, 110, 120).random()
        var c: Int
        do { c = Random.nextInt(12, t / 2) } while (c % 10 == 0 || (t - c) % 10 == 0)
        val a = t - c                                   // 较大的那个加数
        var b: Int
        do { b = Random.nextInt(11, a) } while (b % 10 == 0)   // b < a，保证 a - b 不为负
        return ReorderSetup(a, b, c, t)
    }

    // ④ 选简便方法
    private fun reorderSelect(): Question {
        val s = reorderSetup()
        val correct = "${s.a} + ${s.c} - ${s.b}"
        val wrongs = listOf(
            "${s.a} - (${s.b} + ${s.c})",    // 误把后两个数当成一起减掉
            "${s.a} - ${s.b} - ${s.c}",      // 加号看成减号
            "${s.b} + ${s.c} - ${s.a}"       // 顺序搬错
        )
        return choice("${s.a} - ${s.b} + ${s.c} 怎样算更简便？", correct, wrongs,
            "把能凑整的 ${s.a} 和 ${s.c} 挪到一起先算：$correct")
    }

    // ⑤ 按方法算结果
    private fun reorderCompute(): Question {
        val s = reorderSetup()
        val ans = s.t - s.b                              // = a + c - b
        val text = "简便算：${s.a} - ${s.b} + ${s.c}（先算 ${s.a} + ${s.c} = ${s.t}）= ( )"
        return numChoice(text, ans, listOf(s.a - s.b - s.c, s.a + s.b - s.c, s.a - s.b),
            "${s.a} - ${s.b} + ${s.c} = ${s.a} + ${s.c} - ${s.b} = ${s.t} - ${s.b} = $ans")
    }

    // ============ 公共方法 ============
    // 文字选项（选方法题）：给定正确表达式 + 3 个错误表达式
    private fun choice(text: String, correct: String, wrongs: List<String>, tip: String? = null): Question {
        val opts = (wrongs.filter { it != correct }.distinct().take(3) + correct).shuffled()
        return Question(text, opts, opts.indexOf(correct), tip = tip)
    }

    // 数字选项（算结果 / 填空题）：干扰项优先用常见错误值，不足时用临近数补足
    private fun numChoice(text: String, answer: Int, wrongCandidates: List<Int>, tip: String? = null): Question {
        val wrongs = LinkedHashSet<String>()
        for (w in wrongCandidates) if (w >= 0 && w != answer) wrongs.add(w.toString())
        var d = 1
        while (wrongs.size < 3 && d <= 12) {
            val w = answer + d
            if (w != answer) wrongs.add(w.toString())
            d++
        }
        val opts = (wrongs.toList().take(3) + answer.toString()).shuffled()
        return Question(text, opts, opts.indexOf(answer.toString()), tip = tip)
    }
}
