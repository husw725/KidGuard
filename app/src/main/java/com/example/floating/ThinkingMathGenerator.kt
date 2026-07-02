package com.example.floating

import kotlin.random.Random

/**
 * 数学思维题生成器 — 涵盖人教版二年级经典奥数题型
 * 每种题型都有独立的生成函数，保证题目随机且答案正确
 */
object ThinkingMathGenerator {

    fun generate(): Question {
        val type = Random.nextInt(18)
        val q = when (type) {
            0 -> generateTreePlanting()
            1 -> generateSawLog()
            2 -> generateAgeProblem()
            3 -> generateQueueProblem()
            4 -> generateRopeProblem()
            5 -> generateNumberPattern()
            6 -> generateSumMultiple()
            7 -> generateChickenRabbit()
            8 -> generateMatchstick()
            9 -> generateReverseProblem()
            10 -> generateCircleProblem()
            11 -> generateStairsProblem()
            12 -> generateAllotSameRemainder()
            13 -> generateMeterReading()
            14 -> generateLogicDeduction()
            15 -> generateEquivalentExchange()
            16 -> generateHandshake()
            17 -> generateCountFigures()
            else -> generateAgeDifference()
        }
        return q.copy(tip = q.tip ?: tips[typeNames[type]])
    }
    // 加权版本：很久没出现的题型权重更高，答错过的题型优先
    var lastGeneratedType: String = ""
    private val typeNames = listOf("treePlanting","sawLog","ageProblem","queueProblem","ropeProblem",
        "numberPattern","sumMultiple","chickenRabbit","matchstick","reverseProblem","circleProblem","stairsProblem",
        "allotSameRemainder","meterReading","logicDeduction","equivalentExchange","handshake","countFigures")

    // 各题型答错时的一句话解题思路（点中误区）
    private val tips = mapOf(
        "treePlanting" to "两端都种时，棵数 = 间隔数 + 1",
        "sawLog" to "锯 1 次断成 2 段，段数比次数多 1",
        "ageProblem" to "两个人一起长大，年龄差永远不变",
        "queueProblem" to "从两边数会重复数到自己，记得减 1",
        "ropeProblem" to "用倒推法，从剩下的一步步往回算",
        "numberPattern" to "先看相邻两个数是怎么变化的",
        "sumMultiple" to "先求出 1 倍（1 份）是多少",
        "chickenRabbit" to "假设全是鸡，多出来的腿 ÷ 2 就是兔子数",
        "matchstick" to "第一个用几根，每多一个再加固定的几根",
        "reverseProblem" to "倒推法：从最后的结果一步步往回算",
        "circleProblem" to "围成一圈时，间隔数 = 人数（首尾相连）",
        "stairsProblem" to "从 1 楼到 n 楼，其实只走了 (n−1) 层",
        "allotSameRemainder" to "余数相同：先求两个人数的最小公倍数，再加上余数",
        "meterReading" to "先算这个月用了多少度，再加上月读数，就是这个月的读数",
        "logicDeduction" to "把条件一条条排除，剩下的就是答案",
        "equivalentExchange" to "一步步换：先换成中间的东西，再换成要求的",
        "handshake" to "每人和其他人各一次，但每次握手数了两遍，要除以 2",
        "countFigures" to "按起点一个一个数，别漏别重：从每个点往后数"
    )

    fun generateWeighted(seenRound: Map<String, Int>, errors: Map<String, Int>): Question {
        // 用当前轮次减去 lastSeenRound 算 missed，但这里没有 currentRound
        // 改用 seenRound 差值比较：轮次号越小（越早），权重越高
        // 为简化：直接用 seenRound 的差值来排序
        val maxRound = (seenRound.values.maxOrNull() ?: 0)
        val weights = typeNames.map { tn ->
            val key = "thinking-$tn"
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
        lastGeneratedType = "thinking-${typeNames[idx]}"
        val q = when (idx) {
            0 -> generateTreePlanting()
            1 -> generateSawLog()
            2 -> generateAgeProblem()
            3 -> generateQueueProblem()
            4 -> generateRopeProblem()
            5 -> generateNumberPattern()
            6 -> generateSumMultiple()
            7 -> generateChickenRabbit()
            8 -> generateMatchstick()
            9 -> generateReverseProblem()
            10 -> generateCircleProblem()
            11 -> generateStairsProblem()
            12 -> generateAllotSameRemainder()
            13 -> generateMeterReading()
            14 -> generateLogicDeduction()
            15 -> generateEquivalentExchange()
            16 -> generateHandshake()
            17 -> generateCountFigures()
            else -> generateAgeDifference()
        }
        return q.copy(tip = q.tip ?: tips[typeNames[idx]])
    }

    // ============ ① 植树问题 ============
    /**
     * 在一条长 L 米的路上，每隔 S 米种一棵（两端都种），种几棵？
     * 答案 = L/S + 1
     */
    private fun generateTreePlanting(): Question {
        val spacings = listOf(2, 3, 4, 5, 6)
        val spacing = spacings.random()
        val segments = Random.nextInt(2, 11)
        val length = segments * spacing
        val answer = segments + 1

        val wrongs = listOf(answer - 1, answer + 1, segments, answer + 2)
            .filter { it > 0 && it != answer }
            .distinct()

        val location = listOf("小路", "河堤", "走廊", "街道", "操场跑道").random()

        return createQuestion(
            "在一条长 $length 米的${location}一边种树，每隔 $spacing 米种一棵（两端都种），一共种几棵？",
            "$answer",
            wrongs.map { "$it" }
        )
    }

    // ============ ② 锯木头问题 ============
    /**
     * 锯 n 次变成 n+1 段；锯成 n 段需要 n-1 次
     */
    private fun generateSawLog(): Question {
        return when (Random.nextInt(3)) {
            // 锯了 n 次，变几段？
            0 -> {
                val cuts = Random.nextInt(2, 9)
                val answer = cuts + 1
                createQuestion(
                    "一根木头锯了 $cuts 次，分成了几段？",
                    "$answer",
                    listOf(cuts, cuts + 2, cuts - 1, cuts + 3).filter { it > 0 && it != answer }.map { "$it" }
                )
            }
            // 锯成 n 段，锯几次？
            1 -> {
                val pieces = Random.nextInt(3, 10)
                val answer = pieces - 1
                createQuestion(
                    "把一根木头锯成 $pieces 段，需要锯几次？",
                    "$answer",
                    listOf(pieces, pieces + 1, pieces - 2, pieces + 2).filter { it > 0 && it != answer }.map { "$it" }
                )
            }
            // 锯成 n 段，每次要 t 分钟，共几秒？
            else -> {
                val pieces = Random.nextInt(3, 8)
                val timePerCut = Random.nextInt(2, 6)
                val answer = (pieces - 1) * timePerCut
                val wrongs = listOf(pieces * timePerCut, (pieces - 1) * (timePerCut + 1), (pieces + 1) * timePerCut, pieces * timePerCut + 1)
                    .filter { it != answer && it > 0 }.distinct().take(3)
                createQuestion(
                    "把一根木头锯成 $pieces 段，每锯一次要 $timePerCut 分钟，一共要几分钟？",
                    "$answer",
                    wrongs.map { "$it" }
                )
            }
        }
    }

    // ============ ③ 年龄问题 ============
    private fun generateAgeProblem(): Question {
        return when (Random.nextInt(4)) {
            // x 年后年龄差不变
            0 -> {
                val younger = Random.nextInt(6, 12)
                val diff = Random.nextInt(3, 12)
                val elder = younger + diff
                val years = Random.nextInt(3, 20)
                createQuestion(
                    "今年姐姐 $elder 岁，妹妹 $younger 岁，$years 年后姐姐比妹妹大几岁？",
                    "$diff",
                    listOf(diff + years, diff - years, diff + 1, diff + years / 2).filter { it != diff && it > 0 }.map { "$it" }
                )
            }
            // x 年前年龄差不变
            1 -> {
                val younger = Random.nextInt(6, 12)
                val diff = Random.nextInt(3, 12)
                val elder = younger + diff
                val yearsAgo = Random.nextInt(2, younger)
                createQuestion(
                    "姐姐今年 $elder 岁，妹妹 $younger 岁，$yearsAgo 年前姐姐比妹妹大几岁？",
                    "$diff",
                    listOf(diff + yearsAgo, diff - yearsAgo, diff + 1, diff * 2).filter { it != diff && it > 0 }.map { "$it" }
                )
            }
            // x 年后年龄和
            2 -> {
                val sum = Random.nextInt(25, 45)
                val years = Random.nextInt(2, 8)
                val answer = sum + years * 2
                createQuestion(
                    "今年哥哥和弟弟年龄之和是 $sum 岁，$years 年后他们的年龄和是多少岁？",
                    "$answer",
                    listOf(sum + years, sum + years * 3, sum, sum + years + years).filter { it != answer && it > 0 }.map { "$it" }
                )
            }
            // 几年后 x 倍
            3 -> generateAgeMultiple()
            else -> generateAgeProblem()
        }
    }

    /** 几年后是 x 倍 */
    private fun generateAgeMultiple(): Question {
        // 构造：几年后姐姐是妹妹的 2 倍
        val youngerNow = Random.nextInt(4, 10)
        val years = Random.nextInt(1, youngerNow)
        val youngerThen = youngerNow + years
        val elderThen = youngerThen * 2
        val elderNow = elderThen - years

        val wrongs = listOf(years + 1, years - 1, years * 2, youngerNow - years)
            .filter { it > 0 && it != years }.distinct().take(3)

        return createQuestion(
            "姐姐今年 $elderNow 岁，妹妹 $youngerNow 岁，几年后姐姐的年龄是妹妹的 2 倍？",
            "$years",
            wrongs.map { "$it" }
        )
    }

    // ============ ④ 排队问题 ============
    private fun generateQueueProblem(): Question {
        return when (Random.nextInt(4)) {
            // 前面有 a 人，后面有 b 人，共几人
            0 -> {
                val front = Random.nextInt(2, 12)
                val back = Random.nextInt(2, 12)
                val answer = front + 1 + back
                createQuestion(
                    "同学们排队，小明前面有 $front 人，后面有 $back 人，这一排共有几人？",
                    "$answer",
                    listOf(front + back, front + back + 2, front + back - 1, front * back).filter { it != answer && it > 0 }.map { "$it" }
                )
            }
            // 共 total 人，小明排第 a，后面几人
            1 -> {
                val total = Random.nextInt(12, 30)
                val pos = Random.nextInt(3, total - 2)
                val answer = total - pos
                createQuestion(
                    "一排共有 $total 个小朋友，小明排在第 $pos 个，他后面有几人？",
                    "$answer",
                    listOf(total - pos + 1, total - pos - 1, pos, total - pos + pos).filter { it != answer && it > 0 }.map { "$it" }
                )
            }
            // 从左数第 a，从右数第 b，共几人
            2 -> {
                val left = Random.nextInt(3, 10)
                val right = Random.nextInt(3, 10)
                val answer = left + right - 1
                createQuestion(
                    "小朋友排队，小明从左数是第 $left 个，从右数是第 $right 个，这一排共有几人？",
                    "$answer",
                    listOf(left + right, left + right - 2, left + right + 1, left * right).filter { it != answer && it > 0 }.map { "$it" }
                )
            }
            // 两边都有人
            3 -> {
                val total = Random.nextInt(15, 30)
                val pos = Random.nextInt(3, total - 2)
                val answer = total - pos
                createQuestion(
                    "$total 个同学排成一队，小红从左边数是第 $pos 个，她右边有几个同学？",
                    "$answer",
                    listOf(total - pos + 1, pos - 1, total - pos - 1, pos).filter { it != answer && it > 0 }.map { "$it" }
                )
            }
            else -> generateQueueProblem()
        }
    }

    // ============ ⑤ 绳子/铁丝问题 ============
    private fun generateRopeProblem(): Question {
        return when (Random.nextInt(3)) {
            // 用去一半，再用去剩下的一半，还剩 x 米
            0 -> {
                val remaining = Random.nextInt(3, 15)
                val original = remaining * 4
                val wrongs = listOf(remaining * 2, remaining * 3, remaining * 6, remaining * 8)
                    .filter { it != original }.distinct().take(3)
                createQuestion(
                    "一根铁丝用去一半后，再用去剩下的一半，还剩 $remaining 米，原来长多少米？",
                    "$original",
                    wrongs.map { "$it" }
                )
            }
            // 对折再对折
            1 -> {
                val folded = Random.nextInt(3, 12)
                val original = folded * 4
                val wrongs = listOf(folded * 2, folded * 3, folded * 8, folded + 4)
                    .filter { it != original }.distinct().take(3)
                createQuestion(
                    "一根绳子对折再对折后，量得 $folded 米长，原来长多少米？",
                    "$original",
                    wrongs.map { "$it" }
                )
            }
            // 剪去 x 米，又接上 y 米
            2 -> {
                val cut = Random.nextInt(3, 10)
                val add = Random.nextInt(2, cut)
                val final = Random.nextInt(10, 30)
                val original = final + cut - add
                val wrongs = listOf(final + cut, final - add, final + cut + add, original - 1)
                    .filter { it != original && it > 0 }.distinct().take(3)
                createQuestion(
                    "一根绳子剪去 $cut 米，又接上 $add 米，现在长 $final 米，原来长多少米？",
                    "$original",
                    wrongs.map { "$it" }
                )
            }
            else -> generateRopeProblem()
        }
    }

    // ============ ⑥ 数字规律 ============
    private fun generateNumberPattern(): Question {
        return when (Random.nextInt(5)) {
            // 等差数列
            0 -> {
                val start = Random.nextInt(1, 10)
                val diff = Random.nextInt(2, 8)
                val seq = (0 until 5).map { start + it * diff }
                val answer = start + 5 * diff
                val wrongs = listOf(answer + 1, answer - 1, answer + diff, answer - diff)
                    .filter { it != answer }.distinct().take(3)
                createQuestion(
                    "找规律填数：${seq.joinToString(", ")}, (?)",
                    "$answer",
                    wrongs.map { "$it" }
                )
            }
            // 翻倍（等比）
            1 -> {
                val start = Random.nextInt(1, 4)
                val seq = mutableListOf(start)
                for (i in 1 until 5) seq.add(seq.last() * 2)
                val answer = seq.last() * 2
                val wrongs = listOf(answer + 1, answer - 1, seq.last() * 3, answer / 2)
                    .filter { it != answer && it > 0 }.distinct().take(3)
                createQuestion(
                    "找规律填数：${seq.joinToString(", ")}, (?)",
                    "$answer",
                    wrongs.map { "$it" }
                )
            }
            // 三角形数（1,3,6,10,15,...）相邻差 +2,+3,+4,+5,+6...
            2 -> {
                val seq = mutableListOf(1)
                for (i in 1 until 5) seq.add(seq.last() + i + 1)   // 差为 2,3,4,5 -> seq=[1,3,6,10,15]
                val nextDiff = 6                                    // 下一个差是 +6
                val answer = seq.last() + nextDiff                  // 15 + 6 = 21
                // 干扰项：seq.last()+5(误以为差不变的常见错误) 等
                val wrongs = listOf(seq.last() + 5, answer + 1, answer - 2, seq.last() + 7)
                    .filter { it != answer && it > 0 }.distinct().take(3)
                createQuestion(
                    "找规律填数：${seq.joinToString(", ")}, (?)",
                    "$answer",
                    wrongs.map { "$it" }
                )
            }
            // 平方数（1,4,9,16,25,...）
            3 -> {
                val seq = (1..5).map { it * it }
                val answer = 6 * 6
                val wrongs = listOf(35, 37, 36 + 1, 36 - 2).filter { it != answer }.distinct().take(3)
                createQuestion(
                    "找规律填数：${seq.joinToString(", ")}, (?)",
                    "$answer",
                    wrongs.map { "$it" }
                )
            }
            // 加减交替
            4 -> {
                val start = Random.nextInt(20, 40)
                val add = Random.nextInt(3, 8)
                val sub = Random.nextInt(2, 6)
                val seq = mutableListOf(start)
                for (i in 0 until 5) {
                    if (i % 2 == 0) seq.add(seq.last() + add)
                    else seq.add(seq.last() - sub)
                }
                val answer = if (5 % 2 == 0) seq.last() + add else seq.last() - sub
                val wrongs = listOf(answer + 1, answer - 1, answer + add, answer - sub)
                    .filter { it != answer }.distinct().take(3)
                createQuestion(
                    "找规律填数：${seq.joinToString(", ")}, (?)",
                    "$answer",
                    wrongs.map { "$it" }
                )
            }
            else -> generateNumberPattern()
        }
    }

    // ============ ⑦ 和倍问题 ============
    private fun generateSumMultiple(): Question {
        return when (Random.nextInt(3)) {
            // A + B = sum, A = n * B, 求 B
            0 -> {
                val b = Random.nextInt(2, 10)
                val n = Random.nextInt(2, 5)
                val a = b * n
                val sum = a + b
                val items = listOf(
                    "苹果" to "梨",
                    "大桶水" to "小桶水",
                    "红花" to "黄花",
                    "大箱书" to "小箱书"
                ).random()

                createQuestion(
                    "${items.first}和${items.second}共 $sum 个，${items.first}的数量是${items.second}的 $n 倍，${items.second}有多少个？",
                    "$b",
                    listOf(b + 1, a, b - 1, b * n - 1).filter { it != b && it > 0 }.map { "$it" }
                )
            }
            // 已知差和倍数
            1 -> {
                val b = Random.nextInt(2, 10)
                val n = Random.nextInt(2, 5)
                val a = b * n
                val diff = a - b
                createQuestion(
                    "姐姐的糖果比妹妹多 $diff 颗，姐姐是妹妹的 $n 倍，妹妹有几颗糖果？",
                    "$b",
                    listOf(b + 1, b - 1, a, diff).filter { it != b && it > 0 }.map { "$it" }
                )
            }
            // A 是 B 的 n 倍，A 有 x 个，B 有几个？
            2 -> {
                val b = Random.nextInt(2, 12)
                val n = Random.nextInt(2, 6)
                val a = b * n
                createQuestion(
                    "小明有 $a 张贴纸，是小红的 $n 倍，小红有几张贴纸？",
                    "$b",
                    listOf(b + 1, a, b * 2, a - n).filter { it != b && it > 0 }.map { "$it" }
                )
            }
            else -> generateSumMultiple()
        }
    }

    // ============ ⑧ 鸡兔同笼（简化版） ============
    private fun generateChickenRabbit(): Question {
        return when (Random.nextInt(3)) {
            // 鸡兔同笼
            0 -> {
                val chickens = Random.nextInt(2, 8)
                val rabbits = Random.nextInt(1, 6)
                val heads = chickens + rabbits
                val legs = chickens * 2 + rabbits * 4
                createQuestion(
                    "鸡兔同笼，共有 $heads 个头，$legs 条腿，鸡有几只？",
                    "$chickens",
                    listOf(chickens + 1, chickens - 1, rabbits, heads - rabbits - 1)
                        .filter { it != chickens && it > 0 }.map { "$it" }
                )
            }
            // 自行车和三轮车
            1 -> {
                val bikes = Random.nextInt(3, 10)
                val trikes = Random.nextInt(1, 8)
                val total = bikes + trikes
                val wheels = bikes * 2 + trikes * 3
                createQuestion(
                    "停车场有自行车和三轮车共 $total 辆，一共有 $wheels 个轮子，自行车有几辆？",
                    "$bikes",
                    listOf(bikes + 1, bikes - 1, trikes, total - bikes - 1)
                        .filter { it != bikes && it > 0 }.map { "$it" }
                )
            }
            // 求兔子
            else -> {
                val chickens = Random.nextInt(2, 7)
                val rabbits = Random.nextInt(2, 6)
                val heads = chickens + rabbits
                val legs = chickens * 2 + rabbits * 4
                createQuestion(
                    "鸡兔同笼，共有 $heads 个头，$legs 条腿，兔子有几只？",
                    "$rabbits",
                    listOf(rabbits + 1, rabbits - 1, chickens, heads - chickens - 1)
                        .filter { it != rabbits && it > 0 }.map { "$it" }
                )
            }
        }
    }

    // ============ ⑨ 火柴棒问题 ============
    private fun generateMatchstick(): Question {
        return when (Random.nextInt(3)) {
            // 连在一起的 n 个正方形
            0 -> {
                val n = Random.nextInt(2, 10)
                val answer = 3 * n + 1
                val wrongs = listOf(4 * n, 3 * n, 3 * (n + 1) + 1, 3 * n - 1)
                    .filter { it != answer && it > 0 }.distinct().take(3)
                createQuestion(
                    "用火柴棒搭正方形，搭 1 个要 4 根，搭 2 个连在一起要 7 根，搭 $n 个连在一起要几根？",
                    "$answer",
                    wrongs.map { "$it" }
                )
            }
            // 搭三角形
            1 -> {
                val n = Random.nextInt(2, 8)
                val answer = 2 * n + 1
                val wrongs = listOf(3 * n, 2 * n, 2 * (n + 1) + 1, 2 * n - 1)
                    .filter { it != answer && it > 0 }.distinct().take(3)
                createQuestion(
                    "用火柴棒搭三角形，搭 1 个要 3 根，搭 2 个连在一起要 5 根，搭 $n 个连在一起要几根？",
                    "$answer",
                    wrongs.map { "$it" }
                )
            }
            // 搭 1 行 n 个正方形（上下左右都有）
            2 -> {
                val n = Random.nextInt(2, 6)
                val answer = n * (n + 1) * 2
                val wrongs = listOf(n * n * 2, (n + 1) * (n + 1) * 2, n * 4, answer + 1)
                    .filter { it != answer && it > 0 }.distinct().take(3)
                createQuestion(
                    "搭 $n 行 $n 列的小正方形（网格），最少需要多少根火柴棒？",
                    "$answer",
                    wrongs.map { "$it" }
                )
            }
            else -> generateMatchstick()
        }
    }

    // ============ ⑩ 还原问题（倒推法） ============
    private fun generateReverseProblem(): Question {
        return when (Random.nextInt(3)) {
            // 吃掉一半，又买了 x 颗
            0 -> {
                val half = Random.nextInt(3, 12)
                val added = Random.nextInt(2, 8)
                val final = half + added
                val original = half * 2
                val wrongs = listOf(half * 3, final * 2, original - 1, half + added * 2)
                    .filter { it != original && it > 0 }.distinct().take(3)
                createQuestion(
                    "小欣有一些糖果，吃掉一半后，又买了 $added 颗，现在有 $final 颗，原来有几颗？",
                    "$original",
                    wrongs.map { "$it" }
                )
            }
            // 拿走一半多 x 个
            1 -> {
                val remaining = Random.nextInt(3, 12)
                val extra = Random.nextInt(1, 4)
                val half = remaining + extra
                val original = half * 2
                val wrongs = listOf(remaining * 2, half + extra, remaining + extra * 2, original - extra)
                    .filter { it != original && it > 0 }.distinct().take(3)
                createQuestion(
                    "篮子里有一些苹果，小明拿走了一半多 $extra 个，还剩 $remaining 个，原来有几个？",
                    "$original",
                    wrongs.map { "$it" }
                )
            }
            // 三步倒推
            else -> {
                val start = Random.nextInt(10, 30)
                val sub1 = Random.nextInt(2, start / 2)
                val after1 = start - sub1
                val mul = 2
                val after2 = after1 * mul
                val add = Random.nextInt(2, 10)
                val final = after2 + add
                val wrongs = listOf(start - 1, start + 1, after2, after1 + add)
                    .filter { it != start && it > 0 }.distinct().take(3)
                createQuestion(
                    "小欣有一些钱，先花了 $sub1 元，剩下的钱翻了 $mul 倍，又赚了 $add 元，现在有 $final 元，原来有几元？",
                    "$start",
                    wrongs.map { "$it" }
                )
            }
        }
    }

    // ============ ⑪ 圆圈问题 ============
    /** 围成圆圈的花盆间隔问题 */
    private fun generateCircleProblem(): Question {
        val count = Random.nextInt(4, 15)
        val wrongs = listOf(count + 1, count - 1, count * 2, count / 2)
            .filter { it != count && it > 0 }.distinct().take(3)
        return createQuestion(
            "$count 个小朋友围成一个圆圈玩游戏，每两个小朋友之间放一盆花，一共需要几盆花？",
            "$count",
            wrongs.map { "$it" }
        )
    }

    // ============ ⑫ 爬楼梯问题 ============
    /** 注意：从 1 楼到 n 楼是爬 n-1 层 */
    private fun generateStairsProblem(): Question {
        val floorA = Random.nextInt(2, 6)
        val timePerFloor = Random.nextInt(3, 10)
        val timeA = (floorA - 1) * timePerFloor
        val floorB = floorA + Random.nextInt(2, 5)
        val timeB = (floorB - 1) * timePerFloor
        val wrongs = listOf(floorB * timePerFloor, timeA + timePerFloor, timeA + (floorB - floorA) * timePerFloor, floorB * timePerFloor - timePerFloor)
            .filter { it != timeB && it > 0 }.distinct().take(3)
        return createQuestion(
            "小欣从 1 楼走到 $floorA 楼用了 $timeA 秒，用同样的速度从 1 楼走到 $floorB 楼需要多少秒？",
            "$timeB",
            wrongs.map { "$it" }
        )
    }

    // ============ 年龄差（兜底） ============
    private fun generateAgeDifference(): Question {
        val elder = Random.nextInt(32, 50)
        val younger = Random.nextInt(4, 13)
        val diff = elder - younger
        val wrongs = listOf(diff + 1, diff - 1, elder - younger + 5, diff * 2)
            .filter { it != diff && it > 0 }.distinct().take(3)
        return createQuestion(
            "爸爸今年 $elder 岁，小明 $younger 岁，爸爸比小明大几岁？10 年后爸爸比小明大几岁？",
            "$diff",
            wrongs.map { "$it" }
        )
    }

    // ============ ⑬ 相同余数分配题（最小公倍数）============
    /** 分给 a 人余 r、分给 b 人也余 r → 满足的数 = a,b 的公倍数 + r；最少 = LCM(a,b)+r */
    private fun generateAllotSameRemainder(): Question {
        val pairs = listOf(3 to 4, 4 to 6, 2 to 3, 3 to 5, 4 to 5, 2 to 5)
        val (a, b) = pairs.random()
        val r = Random.nextInt(1, minOf(a, b))
        val lcm = a / gcd(a, b) * b
        return if (Random.nextBoolean()) {
            val ans = lcm + r
            val wrongs = listOf(lcm, lcm + r + a, lcm * 2 + r, r, lcm - r, ans + b)
                .filter { it > 0 && it != ans }.distinct().take(3)
            createQuestion("一些糖果，平均分给 $a 个小朋友余 $r 个，平均分给 $b 个小朋友也余 $r 个。这些糖果最少有多少个？", "$ans", wrongs.map { "$it" })
        } else {
            val k = Random.nextInt(1, 3); val ans = lcm * k + r
            val wrongs = listOf(ans + 1, ans - 1, ans + a, ans + b, lcm * k, ans + lcm + 1, ans + 2)
                .filter { it > 0 && it != ans && !(it % a == r && it % b == r) }.distinct().take(3)
            createQuestion("下面哪个数，平均分给 $a 人和平均分给 $b 人都正好余 $r 个？", "$ans", wrongs.map { "$it" })
        }
    }

    // ============ ⑭ 电表/水表看不清数字 ============
    /** 给上月读数 R、上月用量 U、这月比上月多用/少用 d → 这月读数 = R + (U±d)，其中一位看不清，求那位 */
    private fun generateMeterReading(): Question {
        val r = Random.nextInt(12, 95) * 10          // 上月读数（整十，3 位左右）
        val u = Random.nextInt(3, 12) * 10           // 上月用量（整十）
        var more = Random.nextBoolean()
        val d = Random.nextInt(1, 5) * 10            // 差额（整十）
        if (!more && u - d < 10) more = true         // 保证这月用量为正
        val thisUse = if (more) u + d else u - d
        val thisReading = r + thisUse
        val s = thisReading.toString()
        val pos = if (s.length >= 3) Random.nextInt(1, s.length - 1) else 1  // 取中间位
        val ansDigit = s[pos].toString()
        val shown = s.substring(0, pos) + "□" + s.substring(pos + 1)
        val word = if (more) "多用了" else "少用了"
        val wrongs = (0..9).map { "$it" }.filter { it != ansDigit }.shuffled().take(3)
        return createQuestion(
            "小欣家上月电表读数是 $r 度，上个月用了 $u 度电。这个月比上个月$word $d 度，这个月电表读数是 $shown 度（□ 处看不清）。□ 是几？",
            ansDigit, wrongs)
    }

    // ============ ⑮ 逻辑推理 ============
    private fun generateLogicDeduction(): Question {
        val names = listOf("小欣", "小明", "小红", "小华", "小丽", "小杰")
        return if (Random.nextBoolean()) {
            // 排序比较：x > y > z
            val (x, y, z) = names.shuffled().take(3)
            val rel = listOf(Triple("高", "矮", "个子"), Triple("大", "小", "年纪"), Triple("快", "慢", "跑步")).random()
            val passage = "${x}比${y}${rel.first}，${y}比${z}${rel.first}。"
            if (Random.nextBoolean()) createQuestion("$passage\n\n问题：谁最${rel.second}？", z, listOf(x, y))
            else createQuestion("$passage\n\n问题：谁最${rel.first}？", x, listOf(y, z))
        } else {
            // 身份排除
            val people = names.shuffled().take(3)
            val acts = listOf("钢琴", "画画", "跳舞", "下棋", "游泳").shuffled().take(3)
            val ti = Random.nextInt(3)  // 目标人
            val target = people[ti]; val targetAct = acts[ti]
            val others = acts.filterIndexed { i, _ -> i != ti }
            val passage = "${people[0]}、${people[1]}、${people[2]}三人，分别喜欢${acts[0]}、${acts[1]}、${acts[2]}（每人不一样）。${target}不喜欢${others[0]}，也不喜欢${others[1]}。"
            createQuestion("$passage\n\n问题：${target}喜欢什么？", targetAct, others)
        }
    }

    // ============ ⑯ 等量代换 ============
    private fun generateEquivalentExchange(): Question {
        val fruits = listOf("🍉", "🍎", "🍓", "🍌", "🍐").shuffled()
        val (big, mid, small) = Triple(fruits[0], fruits[1], fruits[2])
        val a = Random.nextInt(2, 5); val b = Random.nextInt(2, 5)
        val ans = a * b
        val wrongs = listOf(a + b, ans + 1, ans - 1, a * b + a).filter { it > 0 && it != ans }.distinct().take(3)
        return createQuestion("1 个 $big = $a 个 $mid，1 个 $mid = $b 个 $small。\n\n问题：1 个 $big = 几个 $small？", "$ans", wrongs.map { "$it" })
    }

    // ============ ⑰ 握手 / 比赛场次 ============
    private fun generateHandshake(): Question {
        val n = Random.nextInt(3, 7)
        val ans = n * (n - 1) / 2
        val wrongs = listOf(n * (n - 1), n, n - 1, ans + 1).filter { it > 0 && it != ans }.distinct().take(3)
        return if (Random.nextBoolean())
            createQuestion("$n 个小朋友，每两个人握一次手，一共要握几次手？", "$ans", wrongs.map { "$it" })
        else
            createQuestion("$n 个球队进行单循环比赛（每两队都要比一场），一共要比几场？", "$ans", wrongs.map { "$it" })
    }

    // ============ ⑱ 数图形 / 数线段 ============
    private fun generateCountFigures(): Question {
        val n = Random.nextInt(3, 7)
        return if (Random.nextBoolean()) {
            val ans = n * (n - 1) / 2
            val wrongs = listOf(n - 1, n, n * (n - 1), ans + 1).filter { it > 0 && it != ans }.distinct().take(3)
            createQuestion("一条直线上有 $n 个点，这些点之间一共能数出几条线段？", "$ans", wrongs.map { "$it" })
        } else {
            val ans = n * (n + 1) / 2
            val wrongs = listOf(n, n + 1, n * n, ans + 1).filter { it > 0 && it != ans }.distinct().take(3)
            createQuestion("把一个长方形平均分成一排 $n 个小格子，图中一共能数出几个长方形？", "$ans", wrongs.map { "$it" })
        }
    }

    private fun gcd(x: Int, y: Int): Int = if (y == 0) x else gcd(y, x % y)

    // ============ 公共方法 ============
    private fun createQuestion(text: String, correct: String, wrongs: List<String>): Question {
        val uniqueWrongs = wrongs.filter { it != correct }.distinct().take(3)
        val allOptions = (uniqueWrongs + correct).shuffled()
        return Question(text, allOptions, allOptions.indexOf(correct))
    }
}
