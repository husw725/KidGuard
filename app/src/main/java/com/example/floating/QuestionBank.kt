package com.example.floating

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import kotlin.random.Random

data class Question(
    val text: String,
    val options: List<String>,
    val correctIndex: Int
) {
    fun toJsonObject(): JSONObject {
        val obj = JSONObject()
        obj.put("text", text)
        val opts = JSONArray()
        options.forEach { opts.put(it) }
        obj.put("options", opts)
        obj.put("correctIndex", correctIndex)
        return obj
    }

    fun shuffledOptions(): Question {
        val correctOption = options[correctIndex]
        val shuffledOpts = options.shuffled()
        return Question(text, shuffledOpts, shuffledOpts.indexOf(correctOption))
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): Question {
            val text = obj.getString("text")
            val optsArray = obj.getJSONArray("options")
            val opts = mutableListOf<String>()
            for (i in 0 until optsArray.length()) opts.add(optsArray.getString(i))
            val correctIndex = obj.getInt("correctIndex")
            return Question(text, opts, correctIndex)
        }
    }
}

object QuestionBank {
    private const val PREFS_NAME = "QuestionBankPrefs"
    private const val KEY_ERROR_RECORDS = "ErrorRecords"
    private const val KEY_TOTAL_QUESTIONS = "TotalQuestions"
    private const val KEY_CLOUD_QUESTIONS = "CloudQuestions"
    private const val KEY_CLOUD_VERSION = "CloudVersion"
    private const val KEY_DAILY_RECORDS = "DailyRecords"
    private const val KEY_LAST_REPORT_TIME = "LastReportTime"
    private const val KEY_DAILY_UNLOCK_COUNT = "DailyUnlockCount"
    private const val KEY_DAILY_UNLOCK_MINUTES = "DailyUnlockMinutes"
    private const val KEY_DAILY_DATA_TIMESTAMP = "DailyDataTimestamp"

    private val cloudQuestions = mutableListOf<Question>()
    private val errorRecords = mutableMapOf<String, Int>()

    fun syncFromClipboard(context: Context, jsonString: String, callback: (success: Boolean, message: String) -> Unit) {
        try {
            val jsonObj = JSONObject(jsonString)
            val newVersion = jsonObj.getInt("version")
            val currentVersion = getCloudVersion(context)
            
            if (newVersion > currentVersion) {
                val questionsArray = jsonObj.getJSONArray("questions")
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                    .putInt(KEY_CLOUD_VERSION, newVersion)
                    .putString(KEY_CLOUD_QUESTIONS, questionsArray.toString())
                    .apply()
                
                loadLocalData(context)
                callback(true, "题库已成功更新至版本: v$newVersion")
            } else {
                callback(false, "版本 v$newVersion 不高于当前 v$currentVersion")
            }
        } catch (e: Exception) {
            callback(false, "解析失败，请检查JSON格式")
        }
    }

    private fun loadLocalData(context: Context) {
        cloudQuestions.clear()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cloudJsonString = prefs.getString(KEY_CLOUD_QUESTIONS, "[]") ?: "[]"
        try {
            val jsonArray = JSONArray(cloudJsonString)
            for (i in 0 until jsonArray.length()) {
                cloudQuestions.add(Question.fromJsonObject(jsonArray.getJSONObject(i)))
            }
        } catch (e: Exception) {}

        errorRecords.clear()
        val errString = prefs.getString(KEY_ERROR_RECORDS, "{}") ?: "{}"
        try {
            val obj = JSONObject(errString)
            for (key in obj.keys()) {
                errorRecords[key] = obj.getInt(key)
            }
        } catch (e: Exception) {}
    }

    fun getRandomQuestions(context: Context, count: Int): List<Question> {
        loadLocalData(context)
        val verbalPool = if (cloudQuestions.isNotEmpty()) cloudQuestions else builtinVerbalQuestions
        val weightedVerbal = verbalPool.map { q -> Pair(q, 1 + (errorRecords[q.text] ?: 0) * 3) }.toMutableList()
        
        val result = mutableListOf<Question>()
        val verbalLimit = count / 2
        
        // --- Added: Inject some dynamic composition questions into the verbal pool ---
        val dynamicVerbalCount = verbalLimit / 3 // About 1/3 of verbal questions are dynamic
        repeat(dynamicVerbalCount) {
            result.add(generateCompositionQuestion())
        }
        
        val remainingVerbalCount = minOf(verbalLimit - result.size, weightedVerbal.size)
        repeat(remainingVerbalCount) {
            val totalWeight = weightedVerbal.sumOf { it.second }
            if (totalWeight <= 0) return@repeat
            var r = Random.nextInt(totalWeight)
            for (i in weightedVerbal.indices) {
                r -= weightedVerbal[i].second
                if (r < 0) {
                    result.add(weightedVerbal[i].first.shuffledOptions())
                    weightedVerbal.removeAt(i)
                    break
                }
            }
        }
        
        val mathNeeded = count - result.size
        repeat(mathNeeded) {
            result.add(generateMathQuestion())
        }
        
        return result.shuffled()
    }

    private fun generateMathQuestion(): Question {
        // --- Modified: Mix original math with new advanced/word problems ---
        return when (Random.nextInt(10)) {
            in 0..5 -> generateGrade2Math(Random.nextInt(5)) // Basic math (addition, multiplication, etc.)
            else -> generateAdvancedMathQuestion() // New advanced logic/word problems
        }
    }

    private fun generateAdvancedMathQuestion(): Question {
        return when (Random.nextInt(10)) {
            0 -> { // Linear Queuing (Total from position)
                val a = Random.nextInt(3, 12); val b = Random.nextInt(3, 12)
                createMathQ("小朋友排队，小欣从左数第 $a 个，从右数第 $b 个，这一排共有多少人？", a + b - 1)
            }
            1 -> { // Linear Queuing (Position from total)
                val total = Random.nextInt(15, 30); val a = Random.nextInt(5, 12)
                createMathQ("一排共有 $total 个小朋友，小欣从左边数是第 $a 个，从右边数她是第几个？", total - a + 1)
            }
            2 -> { // Intervals (Trees)
                val dist = Random.nextInt(3, 8); val gap = Random.nextInt(2, 5)
                val totalLen = dist * gap
                createMathQ("在一条长 $totalLen 米的小路一边种树，每隔 $gap 米种一棵（两端都种），共需多少棵？", dist + 1)
            }
            3 -> { // Wood Sawing
                val pieces = Random.nextInt(3, 8); val perCut = Random.nextInt(2, 6)
                createMathQ("把一根木头锯成 $pieces 段，每锯一次需要 $perCut 分钟，一共需要多少分钟？", (pieces - 1) * perCut)
            }
            4 -> { // Stair Climbing
                val floorA = Random.nextInt(2, 4); val timeA = floorA * 5 // ensure easy division
                val floorB = floorA + Random.nextInt(2, 4)
                val answer = (timeA / (floorA - 1)) * (floorB - 1)
                createMathQ("小欣从 1 楼爬到 $floorA 楼用了 $timeA 秒，以同样的速度爬到 $floorB 楼需要多少秒？", answer)
            }
            5 -> { // Circular (People & Flowers)
                val count = Random.nextInt(5, 12)
                createMathQ("$count 个小朋友围成一个圆圈玩游戏，每两个小朋友之间放一盆花，一共需要多少盆花？", count)
            }
            6 -> { // Age (Constant Difference)
                val dad = Random.nextInt(30, 45); val son = Random.nextInt(5, 12); val years = Random.nextInt(3, 20)
                createMathQ("爸爸今年 $dad 岁，小欣 $son 岁。$years 年后，爸爸比小欣大多少岁？", dad - son)
            }
            7 -> { // Age (Sum change)
                val sum = Random.nextInt(30, 50); val years = Random.nextInt(2, 6)
                createMathQ("今年爸爸和小欣的年龄和是 $sum 岁，$years 年后，他们的年龄和是多少岁？", sum + years * 2)
            }
            8 -> { // Unit Conversion (Mixed)
                val m = Random.nextInt(1, 5); val cm = Random.nextInt(10, 90)
                if (Random.nextBoolean()) createMathQ("$m 米 $cm 厘米 + ${100 - cm} 厘米 = ( ) 米", m + 1)
                else createMathQ("${m * 100 + cm} 厘米 - $m 米 = ( ) 厘米", cm)
            }
            else -> { // Logic Comparison
                val a = Random.nextInt(3, 7); val b = Random.nextInt(3, 7)
                val left = a * b
                val right = (a + 1) * (b - 1)
                val op = if (left > right) ">" else if (left < right) "<" else "="
                val distractors = listOf(">", "<", "=").filter { it != op }
                createQuestion("$a × $b [ ] ${a + 1} × ${b - 1}", op, distractors)
            }
        }
    }


    private fun getRandomName(): String = listOf("小欣", "小明", "小红", "小东", "小亮", "王老师", "李阿姨", "小猫", "小狗").random()

    private fun generateCompositionQuestion(): Question {
        return when (Random.nextInt(8)) {
            0 -> { // Adjectives (Expansion)
                val items = mapOf(
                    "小草" to "绿油油", "天空" to "蓝湛湛", "太阳" to "红艳艳",
                    "云朵" to "白茫茫", "稻田" to "金灿灿", "头发" to "乌黑黑"
                )
                val noun = items.keys.random()
                val correct = items[noun]!!
                val allWrongs = listOf("红通通", "金灿灿", "白茫茫", "蓝盈盈", "绿油油", "黄澄澄", "亮晶晶", "黑乎乎").filter { it != correct }
                createQuestion("( )的$noun", correct, allWrongs.shuffled().take(3))
            }
            1 -> { // Connectives
                val scenarios = listOf(
                    Triple("因为", "所以", listOf("下雨了", "我们不去公园", "太阳出来了", "衣服干了")),
                    Triple("虽然", "但是", listOf("天气很热", "他坚持跑步", "题目很难", "我做出来了")),
                    Triple("不但", "而且", listOf("小明爱学习", "爱劳动", "这朵花很美", "很香"))
                )
                val s = scenarios.random()
                val (part1, part2) = s.third.shuffled().take(2)
                createQuestion("( ) $part1，( ) $part2。", "${s.first}...${s.second}", listOf("如果...就", "只要...就", "一边...一边").shuffled())
            }
            2 -> { // Metaphors
                val bases = listOf(
                    Triple("弯弯的月儿像", "小船", listOf("圆盘", "雨伞", "面条", "镰刀")),
                    Triple("圆圆的荷叶像", "大伞", listOf("小路", "笔", "书本", "盘子")),
                    Triple("红红的枫叶像", "手掌", listOf("小草", "云朵", "石头", "邮票")),
                    Triple("闪闪的星星像", "眼睛", listOf("宝石", "灯泡", "玻璃", "水滴"))
                )
                val m = bases.random()
                createQuestion("${m.first}( )", m.second, m.third.filter { it != m.second }.shuffled().take(3))
            }
            3 -> { // Punctuation (Dynamic)
                val name = getRandomName()
                val actions = listOf("写完作业了", "在公园玩", "吃过饭了", "去学校了")
                val action = actions.random()
                val types = listOf(
                    Triple("$name$action 吗", "？", listOf("。", "！", "，")),
                    Triple("$name$action 呀", "！", listOf("。", "？", "，")),
                    Triple("$name$action", "。", listOf("？", "！", "，"))
                )
                val t = types.random()
                createQuestion("${t.first}( )", t.second, t.third)
            }
            4 -> { // Action Chains (Verbs)
                val humans = listOf("小欣", "小明", "小红", "小东", "小亮", "王老师", "李阿姨")
                val animals = listOf("小猫", "小狗", "小猴子", "小松鼠")
                val scenarios = listOf(
                    // Scenario with animal subject
                    Triple("${animals.random()}( )到门口，( )着尾巴。", "跑/摇", listOf("坐/咬", "跳/看", "走/睡")),
                    // Scenarios with human subjects
                    Triple("${humans.random()}( )起书包，( )向学校。", "背/跑", listOf("拿/走", "提/看", "抱/爬")),
                    Triple("${humans.random()}( )下腰，( )起地上的衣服。", "弯/捡", listOf("坐/拿", "站/看", "蹲/洗"))
                )
                val a = scenarios.random()
                createQuestion(a.first, a.second, a.third)
            }
            5 -> { // Vivid Descriptions (Templates)
                val name = getRandomName()
                val vivids = listOf(
                    Triple("$name 在画画。", "$name 正在认真地画一幅美丽的画。", listOf("$name 画了很多画。", "$name 的画很好看。", "$name 在房间画画。")),
                    Triple("$name 在跑步。", "$name 正在操场上飞快地跑步。", listOf("$name 跑得很快。", "$name 在外面跑步。", "$name 每天都跑步。"))
                )
                val v = vivids.random()
                createQuestion("怎样把“${v.first}”写得更生动？", v.second, v.third)
            }
            6 -> { // Ba/Bei Sentence
                val name = getRandomName()
                val scenarioPool = listOf(
                    "教室" to listOf("打扫了", "整理了"),
                    "房间" to listOf("打扫了", "整理了"),
                    "书本" to listOf("整理了", "拿走了", "弄丢了"),
                    "铅笔" to listOf("整理了", "拿走了", "弄丢了"),
                    "书包" to listOf("整理了", "拿走了", "弄丢了"),
                    "苹果" to listOf("吃掉了", "洗干净了", "拿走了"),
                    "西瓜" to listOf("吃掉了", "切开了", "洗干净了")
                )
                val (obj, actionList) = scenarioPool.random()
                val act = actionList.random()
                val base = "$name$act$obj。"
                val correct = "${obj}被${name}${act}。"
                val wrong1 = "${obj}把${name}${act}。"
                val wrong2 = "${name}把${obj}${act}。"
                val wrong3 = "被${name}${act}${obj}。"
                createQuestion("把“$base”改成“被”字句：", correct, listOf(wrong1, wrong2, wrong3).shuffled())
            }
            else -> { // Logic ordering
                val name = getRandomName()
                val action = listOf("在认真地写作业", "在开心地踢足球", "在仔细地观察小草", "在飞快地穿衣服").random()
                val correct = "$name$action。"
                val options = listOf(
                    correct,
                    "${action}${name}。",
                    "在${name}${action.substring(1)}。",
                    "${action.substring(action.length-2)}$name${action.substring(0, action.length-2)}。"
                )
                createQuestion("下面哪组词语可以排成一句通顺的话？", correct, options.filter { it != correct }.shuffled())
            }
        }
    }



    private fun generateGrade2Math(type: Int): Question {
        return when (type) {
            0 -> {
                val a = Random.nextInt(10, 100); val b = Random.nextInt(10, 100)
                if (Random.nextBoolean()) createMathQ("$a + $b = ?", a + b)
                else createMathQ("${maxOf(a, b)} - ${minOf(a, b)} = ?", maxOf(a, b) - minOf(a, b))
            }
            1 -> {
                val a = Random.nextInt(2, 10); val b = Random.nextInt(2, 10)
                createMathQ("$a × $b = ?", a * b)
            }
            2 -> {
                val divisor = Random.nextInt(3, 10); val quotient = Random.nextInt(2, 10); val rem = Random.nextInt(1, divisor)
                val dividend = divisor * quotient + rem
                createQuestion("$dividend ÷ $divisor = ?", "${quotient}余${rem}", listOf("${quotient}余${(rem+1)%divisor}", "${quotient+1}余${rem}", "${quotient-1}余${rem+1}"))
            }
            else -> {
                val a = Random.nextInt(2, 10); val b = Random.nextInt(2, 10); val c = Random.nextInt(2, 20)
                if (Random.nextBoolean()) createMathQ("$a × $b + $c = ?", a * b + c)
                else createMathQ("${a * b} - ${minOf(c, a * b - 1)} = ?", a * b - minOf(c, a * b - 1))
            }
        }
    }

    private fun createMathQ(text: String, answer: Int): Question {
        val correct = answer.toString()
        val wrongs = mutableSetOf<String>()
        val variance = if (answer > 100) 20 else if (answer > 20) 10 else 5
        while (wrongs.size < 3) {
            val w = (answer + Random.nextInt(-variance, variance + 1)).toString()
            if (w != correct && w.toInt() >= 0) wrongs.add(w)
        }
        return createQuestion(text, correct, wrongs.toList())
    }

    private fun createQuestion(text: String, correct: String, wrongs: List<String>): Question {
        val uniqueWrongs = wrongs.filter { it != correct }.distinct()
        val allOptions = (uniqueWrongs.take(3) + correct).shuffled()
        return Question(text, allOptions, allOptions.indexOf(correct))
    }

    fun recordResult(context: Context, question: Question, isCorrect: Boolean, isMath: Boolean) {
        val count = errorRecords[question.text] ?: 0
        errorRecords[question.text] = if (isCorrect) maxOf(0, count - 1) else count + 1
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val obj = JSONObject()
        errorRecords.forEach { (k, v) -> if (v > 0) obj.put(k, v) }
        
        val editor = prefs.edit()
        editor.putString(KEY_ERROR_RECORDS, obj.toString())

        val dailyRecordsString = prefs.getString(KEY_DAILY_RECORDS, "[]") ?: "[]"
        try {
            val dailyRecords = JSONArray(dailyRecordsString)
            val recordObj = JSONObject()
            recordObj.put("q", question.text)
            recordObj.put("ans", question.options[question.correctIndex])
            recordObj.put("isCorrect", isCorrect)
            recordObj.put("isMath", isMath)
            dailyRecords.put(recordObj)
            editor.putString(KEY_DAILY_RECORDS, dailyRecords.toString())
            editor.putLong(KEY_DAILY_DATA_TIMESTAMP, System.currentTimeMillis())
        } catch (e: Exception) {}
        editor.apply()
    }

    fun recordUnlockEvent(context: Context, minutes: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentCount = prefs.getInt(KEY_DAILY_UNLOCK_COUNT, 0)
        val currentMinutes = prefs.getInt(KEY_DAILY_UNLOCK_MINUTES, 0)
        
        prefs.edit()
            .putInt(KEY_DAILY_UNLOCK_COUNT, currentCount + 1)
            .putInt(KEY_DAILY_UNLOCK_MINUTES, currentMinutes + minutes)
            .putLong(KEY_DAILY_DATA_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    fun getDailyReport(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastReportTime = prefs.getLong(KEY_LAST_REPORT_TIME, 0L)
        val now = Calendar.getInstance()
        val last = Calendar.getInstance().apply { timeInMillis = lastReportTime }

        // If last report was sent today, do nothing.
        if (now.get(Calendar.DAY_OF_YEAR) == last.get(Calendar.DAY_OF_YEAR) &&
            now.get(Calendar.YEAR) == last.get(Calendar.YEAR)) {
            return null
        }
        
        // If there is data, but it's from a previous day, clear it and return null.
        val dataCalendar = Calendar.getInstance().apply { timeInMillis = prefs.getLong(KEY_DAILY_DATA_TIMESTAMP, 0L) }
        if (hasDailyData(context) &&
            (now.get(Calendar.DAY_OF_YEAR) != dataCalendar.get(Calendar.DAY_OF_YEAR) ||
             now.get(Calendar.YEAR) != dataCalendar.get(Calendar.YEAR))) {
            clearDailyReport(context)
            return null
        }

        return getRawDailyReport(context)
    }

    fun getRawDailyReport(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val dailyRecordsString = prefs.getString(KEY_DAILY_RECORDS, "[]") ?: "[]"
        val totalUnlocks = prefs.getInt(KEY_DAILY_UNLOCK_COUNT, 0)
        val totalMinutes = prefs.getInt(KEY_DAILY_UNLOCK_MINUTES, 0)

        if (JSONArray(dailyRecordsString).length() == 0 && totalUnlocks == 0) {
            return "### 📊 小欣实时学习简报\n\n今日暂无学习记录。加油哦！💪"
        }

        try {
            val records = JSONArray(dailyRecordsString)
            val mathLines = mutableListOf<String>()
            val verbalLines = mutableListOf<String>()
            val errorLines = mutableListOf<String>()

            for (i in 0 until records.length()) {
                val r = records.getJSONObject(i)
                val q = r.getString("q")
                val ans = r.getString("ans")
                val ok = r.getBoolean("isCorrect")
                val isMath = r.getBoolean("isMath")
                
                val status = if (ok) "✅" else "❌"
                val entry = "$q ($ans) $status"

                if (isMath) mathLines.add(entry) else verbalLines.add(entry)
                if (!ok) errorLines.add(entry)
            }

            val sb = StringBuilder()
            sb.append("### 📊 小欣 24h 学习日报\n\n")
            sb.append("**📱 今日手机使用:**\n")
            sb.append("- 成功解锁次数: **$totalUnlocks 次**\n")
            sb.append("- 累计获得时长: **$totalMinutes 分钟**\n\n")

            sb.append("**📐 数学记录:**\n")
            if (mathLines.isEmpty()) {
                 sb.append("- 无\n")
            } else {
                for (i in mathLines.indices step 2) {
                    val first = mathLines[i]
                    val second = if (i + 1 < mathLines.size) " | " + mathLines[i + 1] else ""
                    sb.append("- $first$second\n")
                }
            }
            sb.append("\n")

            sb.append("**📖 语文记录:**\n")
            if (verbalLines.isEmpty()) {
                sb.append("- 无\n")
            } else {
                verbalLines.forEach { sb.append("- $it\n") }
            }
            sb.append("\n")

            sb.append("**⚠️ 错题汇总:**\n")
            if (errorLines.isEmpty()) {
                sb.append("- 无错题 🎉\n")
            } else {
                errorLines.forEach { sb.append("- $it\n") }
            }

            return sb.toString()
        } catch (e: Exception) { return null }
    }




    fun clearDailyReport(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_DAILY_RECORDS, "[]")
            .putInt(KEY_DAILY_UNLOCK_COUNT, 0)
            .putInt(KEY_DAILY_UNLOCK_MINUTES, 0)
            .putLong(KEY_DAILY_DATA_TIMESTAMP, 0L)
            .apply()
    }

    fun hasDailyData(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return (prefs.getString(KEY_DAILY_RECORDS, "[]") ?: "[]").length > 2 ||
               prefs.getInt(KEY_DAILY_UNLOCK_COUNT, 0) > 0
    }


    fun isMathQuestion(text: String): Boolean {
        return text.contains("+") || text.contains("-") || text.contains("×") || 
               text.contains("÷") || text.contains("=") || text.contains("多少") || 
               text.contains("几") || text.contains("米") || text.contains("厘米") || 
               text.contains("岁") || text.contains("角")
    }
    fun getTotalQuestionConfig(context: Context): Int = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_TOTAL_QUESTIONS, 20)
    fun setTotalQuestionConfig(context: Context, count: Int) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_TOTAL_QUESTIONS, count).apply()
    fun getCloudVersion(context: Context): Int = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_CLOUD_VERSION, 0)
    fun getLastReportTime(context: Context): Long = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getLong(KEY_LAST_REPORT_TIME, 0L)
    fun setLastReportTime(context: Context, time: Long) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putLong(KEY_LAST_REPORT_TIME, time).apply()
}