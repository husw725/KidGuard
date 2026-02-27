package com.example.floating

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import kotlin.random.Random
import kotlin.concurrent.thread

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
    private const val KEY_CUSTOM_QUESTIONS = "CustomQuestions"
    private const val KEY_ERROR_RECORDS = "ErrorRecords"
    private const val KEY_TOTAL_QUESTIONS = "TotalQuestions"
    private const val KEY_CLOUD_QUESTIONS = "CloudQuestions"
    private const val KEY_CLOUD_VERSION = "CloudVersion"
    
    private const val CLOUD_URL = "https://gitee.com/husw725/codes/i3hokdrwm7el20xsnab5u13/raw?blob_name=gistfile1.txt"

    // 默认内置的语文题目，以防断网且初次安装无缓存
    private val builtinVerbalQuestions = listOf(
        Question("下列哪个字是左右结构？", listOf("国", "森", "林", "苗"), 2),
        Question("“白日依山尽”的下一句是？", listOf("黄河入海流", "欲穷千里目", "更上一层楼", "疑是银河落九天"), 0),
        Question("找出反义词：快 —— ()", listOf("慢", "高", "远", "长"), 0),
        Question("“春眠不觉晓”的作者是谁？", listOf("李白", "杜甫", "孟浩然", "白居易"), 2),
        Question("“十年树木，”下一句是？", listOf("百年树人", "岁岁平安", "寸金难买寸光阴", "一寸光阴一寸金"), 0),
        Question("“赠人玫瑰，”下一句是？", listOf("手有余香", "香气扑鼻", "留有余香", "手留余香"), 0),
        Question("“人之初，”下一句是？", listOf("性相近", "性本善", "习相远", "苟不教"), 1)
    )

    private val customQuestions = mutableListOf<Question>()
    private val cloudQuestions = mutableListOf<Question>()
    private val errorRecords = mutableMapOf<String, Int>()

    fun initAndSyncCloud(context: Context) {
        loadLocalData(context)
        thread {
            try {
                val url = URL(CLOUD_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                
                if (connection.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = reader.readText()
                    reader.close()
                    
                    val jsonObj = JSONObject(response)
                    val cloudVersion = jsonObj.getInt("version")
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val localVersion = prefs.getInt(KEY_CLOUD_VERSION, 0)
                    
                    if (cloudVersion > localVersion) {
                        val questionsArray = jsonObj.getJSONArray("questions")
                        prefs.edit()
                            .putInt(KEY_CLOUD_VERSION, cloudVersion)
                            .putString(KEY_CLOUD_QUESTIONS, questionsArray.toString())
                            .apply()
                        
                        // 同步成功后，重新加载本地数据到内存
                        Handler(Looper.getMainLooper()).post {
                            loadLocalData(context)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace() // 无网或异常时静默失败，使用本地兜底
            }
        }
    }

    private fun loadLocalData(context: Context) {
        customQuestions.clear()
        cloudQuestions.clear()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        val customJsonString = prefs.getString(KEY_CUSTOM_QUESTIONS, "[]") ?: "[]"
        try {
            val jsonArray = JSONArray(customJsonString)
            for (i in 0 until jsonArray.length()) {
                customQuestions.add(Question.fromJsonObject(jsonArray.getJSONObject(i)))
            }
        } catch (e: Exception) { e.printStackTrace() }

        val cloudJsonString = prefs.getString(KEY_CLOUD_QUESTIONS, "[]") ?: "[]"
        try {
            val jsonArray = JSONArray(cloudJsonString)
            for (i in 0 until jsonArray.length()) {
                cloudQuestions.add(Question.fromJsonObject(jsonArray.getJSONObject(i)))
            }
        } catch (e: Exception) { e.printStackTrace() }

        errorRecords.clear()
        val errString = prefs.getString(KEY_ERROR_RECORDS, "{}") ?: "{}"
        try {
            val obj = JSONObject(errString)
            for (key in obj.keys()) {
                errorRecords[key] = obj.getInt(key)
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun saveCustomQuestion(context: Context, question: Question) {
        customQuestions.add(question)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        for (q in customQuestions) {
            jsonArray.put(q.toJsonObject())
        }
        prefs.edit().putString(KEY_CUSTOM_QUESTIONS, jsonArray.toString()).apply()
    }

    /**
     * 智能年级判断引擎
     * 基于预设的小欣的学涯时间表，根据当前系统时间推算她的学习阶段。
     * 当前基准：2026年上半年为二年级下。
     * - Grade 2: 2026年8月31日及之前
     * - Grade 3: 2026年9月1日 - 2027年8月31日
     * - Grade 4: 2027年9月1日之后
     */
    private fun getCurrentGrade(): Int {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1 // Calendar.MONTH is 0-based

        if (year < 2026 || (year == 2026 && month < 9)) {
            return 2 // 二年级
        } else if (year == 2026 || (year == 2027 && month < 9)) {
            return 3 // 三年级
        } else {
            return 4 // 四年级及以上
        }
    }

    private fun generateMathQuestion(): Question {
        val grade = getCurrentGrade()
        val type = Random.nextInt(5)
        
        return when (grade) {
            2 -> generateGrade2Math(type)
            3 -> generateGrade3Math(type)
            else -> generateGrade4Math(type)
        }
    }

    // ================== 二年级难度 (当前状态) ==================
    // 两位数加减法，九九乘法表，简单的有余数除法，两位数以内的基础混合运算
    private fun generateGrade2Math(type: Int): Question {
        return when (type) {
            0 -> { // 加减法 (两位数)
                val a = Random.nextInt(10, 100)
                val b = Random.nextInt(10, 100)
                if (Random.nextBoolean()) {
                    createMathQ("$a + $b = ?", a + b)
                } else {
                    val big = maxOf(a, b); val small = minOf(a, b)
                    createMathQ("$big - $small = ?", big - small)
                }
            }
            1 -> { // 乘法 (表内乘法)
                val a = Random.nextInt(2, 10); val b = Random.nextInt(2, 10)
                createMathQ("$a × $b = ?", a * b)
            }
            2 -> { // 有余数除法 (表内被除数，不大于90)
                val divisor = Random.nextInt(3, 10)
                val quotient = Random.nextInt(2, 10)
                val remainder = Random.nextInt(1, divisor)
                val dividend = divisor * quotient + remainder
                val correct = "${quotient}余${remainder}"
                val wrong1 = "${quotient}余${(remainder+1)%divisor}"
                val wrong2 = "${quotient+1}余${remainder}"
                val wrong3 = "${quotient-1}余${remainder+1}"
                createQuestion("$dividend ÷ $divisor = ?", correct, listOf(wrong1, wrong2, wrong3))
            }
            3 -> { // 乘加/乘减 (无括号，结果在百以内)
                val a = Random.nextInt(2, 10); val b = Random.nextInt(2, 10)
                val c = Random.nextInt(2, 20)
                if (Random.nextBoolean()) {
                    createMathQ("$a × $b + $c = ?", a * b + c)
                } else {
                    val prod = a * b; val sub = minOf(c, prod - 1)
                    createMathQ("$prod - $sub = ?", prod - sub)
                }
            }
            else -> { // 除加/除减 (无括号)
                val b = Random.nextInt(2, 10); val a = b * Random.nextInt(2, 10)
                val c = Random.nextInt(2, 20)
                if (Random.nextBoolean()) {
                    createMathQ("$a ÷ $b + $c = ?", a / b + c)
                } else {
                    val res = a / b; val sub = minOf(c, res - 1)
                    createMathQ("$res - $sub = ?", res - sub)
                }
            }
        }
    }

    // ================== 三年级难度 (今年9月自动解锁) ==================
    // 三位数加减法，两位数乘一位数，三位数除以一位数，带小括号的混合运算，同分母分数加减法入门
    private fun generateGrade3Math(type: Int): Question {
        return when (type) {
            0 -> { // 三位数加减法
                val a = Random.nextInt(100, 1000)
                val b = Random.nextInt(100, 1000)
                if (Random.nextBoolean()) {
                    createMathQ("$a + $b = ?", a + b)
                } else {
                    val big = maxOf(a, b); val small = minOf(a, b)
                    createMathQ("$big - $small = ?", big - small)
                }
            }
            1 -> { // 两位数乘一位数 / 三位数除以一位数
                if (Random.nextBoolean()) {
                    val a = Random.nextInt(11, 100); val b = Random.nextInt(2, 10)
                    createMathQ("$a × $b = ?", a * b)
                } else {
                    val divisor = Random.nextInt(2, 10)
                    val quotient = Random.nextInt(11, 150)
                    val dividend = divisor * quotient
                    createMathQ("$dividend ÷ $divisor = ?", quotient)
                }
            }
            2 -> { // 同分母分数加减法 (结果不化简，只做基础考查)
                val denominator = Random.nextInt(5, 15)
                val num1 = Random.nextInt(1, denominator)
                val num2 = Random.nextInt(1, denominator)
                if (num1 + num2 < denominator) {
                    val ans = num1 + num2
                    createQuestion("$num1/$denominator + $num2/$denominator = ?", "$ans/$denominator", 
                        listOf("${ans+1}/$denominator", "$ans/${denominator*2}", "${ans-1}/$denominator"))
                } else {
                    val big = maxOf(num1, num2); val small = minOf(num1, num2)
                    if (big == small) {
                         createQuestion("$big/$denominator - $small/$denominator = ?", "0", listOf("1", "2/$denominator", "1/$denominator"))
                    } else {
                        val ans = big - small
                        createQuestion("$big/$denominator - $small/$denominator = ?", "$ans/$denominator", 
                            listOf("${ans+1}/$denominator", "0", "${ans-1}/$denominator"))
                    }
                }
            }
            3 -> { // 带小括号的加减乘混合运算
                val a = Random.nextInt(10, 50)
                val b = Random.nextInt(5, 30)
                val c = Random.nextInt(2, 10)
                if (Random.nextBoolean()) {
                    createMathQ("($a + $b) × $c = ?", (a + b) * c)
                } else {
                    val big = maxOf(a, b); val small = minOf(a, b)
                    createMathQ("($big - $small) × $c = ?", (big - small) * c)
                }
            }
            else -> { // 带小括号的加减除混合运算 (保证能整除)
                val divisor = Random.nextInt(2, 10)
                val quotient = Random.nextInt(5, 30)
                val targetSum = divisor * quotient
                val a = Random.nextInt(1, targetSum)
                val b = targetSum - a
                createMathQ("($a + $b) ÷ $divisor = ?", quotient)
            }
        }
    }

    // ================== 四年级难度 (明年9月自动解锁) ==================
    // 两位数乘两位数，三位数除以两位数，基础小数加减法，大数字四则混合运算
    private fun generateGrade4Math(type: Int): Question {
        return when (type) {
            0 -> { // 基础小数加减法 (一位小数)
                val a = Random.nextInt(10, 100) / 10.0
                val b = Random.nextInt(10, 100) / 10.0
                if (Random.nextBoolean()) {
                    val correct = String.format("%.1f", a + b)
                    createQuestion("$a + $b = ?", correct, 
                        listOf(String.format("%.1f", a + b + 1.0), String.format("%.1f", a + b - 0.1), String.format("%.1f", a + b + 0.1)))
                } else {
                    val big = maxOf(a, b); val small = minOf(a, b)
                    val correct = String.format("%.1f", big - small)
                    createQuestion("$big - $small = ?", correct, 
                        listOf(String.format("%.1f", big - small + 1.0), String.format("%.1f", big - small - 0.1), String.format("%.1f", big - small + 0.1)))
                }
            }
            1 -> { // 两位数乘两位数
                val a = Random.nextInt(11, 100); val b = Random.nextInt(11, 100)
                createMathQ("$a × $b = ?", a * b)
            }
            2 -> { // 三位数除以两位数 (无余数)
                val divisor = Random.nextInt(11, 100)
                val quotient = Random.nextInt(2, 50)
                val dividend = divisor * quotient
                createMathQ("$dividend ÷ $divisor = ?", quotient)
            }
            3 -> { // 大数字四则混合
                val a = Random.nextInt(100, 500)
                val b = Random.nextInt(10, 50)
                val c = Random.nextInt(10, 50)
                createMathQ("$a + $b × $c = ?", a + b * c)
            }
            else -> { // 复杂括号运算
                val a = Random.nextInt(50, 200)
                val divisor = Random.nextInt(10, 50)
                val quotient = Random.nextInt(2, 20)
                val targetSub = divisor * quotient
                val b = a - targetSub
                createMathQ("($a - $b) ÷ $divisor = ?", quotient)
            }
        }
    }

    private fun createMathQ(text: String, answer: Int): Question {
        val correct = answer.toString()
        val wrongs = mutableSetOf<String>()
        // 智能生成错项：围绕正确答案波动，防止太离谱被轻易排除
        val variance = if (answer > 100) 20 else if (answer > 20) 10 else 5
        while (wrongs.size < 3) {
            val w = (answer + Random.nextInt(-variance, variance + 1)).toString()
            if (w != correct && w.toInt() >= 0) wrongs.add(w)
        }
        return createQuestion(text, correct, wrongs.toList())
    }

    private fun createQuestion(text: String, correct: String, wrongs: List<String>): Question {
        val allOptions = (wrongs.take(3) + correct).shuffled()
        return Question(text, allOptions, allOptions.indexOf(correct))
    }

    fun getRandomQuestions(context: Context, count: Int): List<Question> {
        loadLocalData(context)
        val verbalCount = count / 2
        val mathCount = count - verbalCount

        // 优先使用云端题库，如果为空则使用内置兜底题库
        val baseVerbalPool = if (cloudQuestions.isNotEmpty()) cloudQuestions else builtinVerbalQuestions
        val verbalPool = (baseVerbalPool + customQuestions)
        
        val weightedVerbal = verbalPool.map { q ->
            val errs = errorRecords[q.text] ?: 0
            Pair(q, 1 + errs * 3)
        }.toMutableList()

        val result = mutableListOf<Question>()
        
        // 抽取语文及配置题目
        repeat(minOf(verbalCount, weightedVerbal.size)) {
            val totalWeight = weightedVerbal.sumOf { it.second }
            var r = Random.nextInt(totalWeight)
            for (i in weightedVerbal.indices) {
                r -= weightedVerbal[i].second
                if (r < 0) {
                    // 动态打乱文字题的选项顺序，确保选项随机但正确答案对应
                    result.add(weightedVerbal[i].first.shuffledOptions())
                    weightedVerbal.removeAt(i)
                    break
                }
            }
        }

        // 抽取数学题 (动态难度，算法内部已经将选项打乱)
        repeat(mathCount) {
            result.add(generateMathQuestion())
        }

        return result.shuffled()
    }

    fun recordResult(context: Context, question: Question, isCorrect: Boolean) {
        val count = errorRecords[question.text] ?: 0
        errorRecords[question.text] = if (isCorrect) maxOf(0, count - 1) else count + 1
        
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val obj = JSONObject()
        errorRecords.forEach { (k, v) -> if (v > 0) obj.put(k, v) }
        prefs.edit().putString(KEY_ERROR_RECORDS, obj.toString()).apply()
    }

    fun getTotalQuestionConfig(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_TOTAL_QUESTIONS, 20)

    fun setTotalQuestionConfig(context: Context, count: Int) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_TOTAL_QUESTIONS, count).apply()
}