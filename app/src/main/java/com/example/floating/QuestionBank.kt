package com.example.floating

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import kotlin.random.Random

data class Question(
    val text: String,
    val options: List<String>,
    val correctIndex: Int,
    val audioWord: String? = null,  // 非空 = “听音选图”题：朗读该英文单词，选项为 emoji 图片
    val tip: String? = null,        // 答错时显示的一句话讲解（点中误区）
    val inputAnswer: String? = null, // 非空 = 手输得数题（数字键盘），不使用 options
    val masteryKey: String? = null   // 非空 = 三上必背内容（先教后测），答题后按此键更新掌握度
) {
    init {
        if (inputAnswer == null) {  // 输入题不依赖 options
            require(options.size in 2..4) { "Options must contain 2 to 4 items" }
            require(options.distinct().size == options.size) { "Options must be unique" }
            require(correctIndex in options.indices) { "CorrectIndex out of bounds" }
        }
    }

    fun toJsonObject(): JSONObject {
        val obj = JSONObject()
        obj.put("text", text)
        val opts = JSONArray()
        options.forEach { opts.put(it) }
        obj.put("options", opts)
        obj.put("correctIndex", correctIndex)
        if (audioWord != null) obj.put("audioWord", audioWord)
        if (tip != null) obj.put("tip", tip)
        if (inputAnswer != null) obj.put("inputAnswer", inputAnswer)
        if (masteryKey != null) obj.put("masteryKey", masteryKey)
        return obj
    }

    // 重构：返回一个全新的、选项唯一的Question实例
    fun shuffledOptions(): Question {
        val correctOption = options[correctIndex]
        // 先获取除了正确答案外的其他选项，保证4个唯一
        val otherOptions = options.filter { it != correctOption }.toMutableList()
        // 如果不足3个，补齐随机词汇（简单模拟，可根据需要扩展）
        val filler = listOf("一", "二", "三", "四", "五", "六", "七", "八", "九", "十")
        while (otherOptions.size < 3) {
            val candidate = filler.random()
            if (!otherOptions.contains(candidate) && candidate != correctOption) {
                otherOptions.add(candidate)
            }
        }
        
        val newOptions = (otherOptions.take(3) + correctOption).shuffled()
        return Question(text, newOptions, newOptions.indexOf(correctOption), audioWord, tip, masteryKey = masteryKey)
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): Question {
            val text = obj.getString("text")
            val optsArray = obj.getJSONArray("options")
            val opts = mutableListOf<String>()
            for (i in 0 until optsArray.length()) opts.add(optsArray.getString(i))
            val correctIndex = obj.getInt("correctIndex")
            val audioWord = if (obj.has("audioWord")) obj.getString("audioWord") else null
            val tip = if (obj.has("tip")) obj.getString("tip") else null
            val inputAnswer = if (obj.has("inputAnswer")) obj.getString("inputAnswer") else null
            val masteryKey = if (obj.has("masteryKey")) obj.getString("masteryKey") else null
            if (inputAnswer != null) return Question(text, emptyList(), 0, audioWord, tip, inputAnswer, masteryKey)
            // 保持原始数据完整性，如果原始数据有问题，强制修正
            return try {
                Question(text, opts, correctIndex, audioWord, tip, masteryKey = masteryKey)
            } catch (e: Exception) {
                // 简单处理数据源错误，如果导入数据不规范则返回默认结构
                val safeOpts = (opts.distinct().take(3) + "补全").shuffled()
                Question(text, safeOpts, safeOpts.indexOf(safeOpts.first()))
            }
        }
    }
}

object QuestionBank {
    private const val PREFS_NAME = "QuestionBankPrefs"
    private const val KEY_ERROR_RECORDS = "ErrorRecords"
    private const val KEY_CLOUD_QUESTIONS = "CloudQuestions"
    private const val KEY_CLOUD_VERSION = "CloudVersion"
    private const val KEY_DAILY_RECORDS = "DailyRecords"
    private const val KEY_LAST_REPORT_TIME = "LastReportTime"
    private const val KEY_DAILY_UNLOCK_COUNT = "DailyUnlockCount"
    private const val KEY_DAILY_UNLOCK_MINUTES = "DailyUnlockMinutes"
    // 注意：旧版把 "DailyDataTimestamp" 存成 Long（毫秒），getInt 会 ClassCastException。
    // 这里换一个全新的 Int 键存今天的 dayKey，绕开旧残留值。
    private const val KEY_DAILY_DATA_TIMESTAMP = "DailyDayKeyInt"
    private const val KEY_DAILY_ANSWERED = "DailyAnswered"
    private const val KEY_DAILY_CORRECT = "DailyCorrect"
    private const val KEY_DAILY_UNLOCK_LIMIT = "DailyUnlockLimit"  // 每日可解锁次数上限（默认 3，可远程改）
    private const val KEY_DAILY_BONUS = "DailyBonusUnlock"         // 今日临时奖励次数（远程“加一次”，按天清零）
    private const val KEY_LAST_VERSION = "LastAppVersion"          // 上次运行的 App 版本号（更新后清零今日次数）
    private const val KEY_TOTAL_QUESTIONS = "TotalQuestions"
    private const val KEY_LAST_SEEN_ROUND = "LastSeenRound"
    private const val KEY_MATH_TYPE_SEEN = "MathTypeSeen"
    private const val KEY_MATH_TYPE_ERRORS = "MathTypeErrors"
    private const val KEY_QUIZ_ROUND = "QuizRound"
    private const val KEY_DIFFICULTY = "Difficulty"
    private const val KEY_CONSEC_CORRECT = "ConsecCorrect"
    private const val KEY_QUIZ_FAIL = "QuizFailCount"

    fun getQuizFailCount(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_QUIZ_FAIL, 0)

    fun setQuizFailCount(context: Context, n: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_QUIZ_FAIL, n).apply()
    }

    // ===== ② 正向激励：连续学习天数 + 累计星星 =====
    private const val KEY_STREAK = "StudyStreak"
    private const val KEY_STREAK_DATE = "StreakDate"
    private const val KEY_STARS = "StudyStars"

    private fun dayKey(offsetDays: Int = 0): Int {
        val c = Calendar.getInstance()
        if (offsetDays != 0) c.add(Calendar.DAY_OF_MONTH, offsetDays)
        return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)
    }

    // 通关时调用：更新连续天数与星星（1 + 连击/BOSS/宝箱的额外星），返回 (连续天数, 总星星)
    fun recordPass(context: Context, bonusStars: Int = 0): Pair<Int, Int> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = dayKey()
        val lastDate = prefs.getInt(KEY_STREAK_DATE, 0)
        var streak = prefs.getInt(KEY_STREAK, 0)
        if (lastDate != today) {                       // 同一天多次通关只算一天
            streak = if (lastDate == dayKey(-1)) streak + 1 else 1
        }
        val stars = prefs.getInt(KEY_STARS, 0) + 1 + bonusStars
        prefs.edit().putInt(KEY_STREAK, streak).putInt(KEY_STREAK_DATE, today).putInt(KEY_STARS, stars).apply()
        return Pair(streak, stars)
    }

    fun getStars(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_STARS, 0)

    // ===== ④ 家长辅导依据：薄弱点（按数学题型错误次数取前 2）=====
    fun getWeakPointsText(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val mte = prefs.getString(KEY_MATH_TYPE_ERRORS, "{}") ?: "{}"
        val counts = mutableListOf<Pair<String, Int>>()
        try {
            val obj = JSONObject(mte)
            for (k in obj.keys()) counts.add(k to obj.getInt(k))
        } catch (e: Exception) {}
        val top = counts.filter { it.second > 0 }.sortedByDescending { it.second }
            .map { weakName(it.first) }.distinct().take(2)
        return if (top.isEmpty()) null else top.joinToString("、")
    }

    private fun weakName(key: String): String = when {
        "smartcalc" in key -> "巧算/简便计算"
        "tree" in key -> "植树问题"
        "saw" in key -> "锯木问题"
        "queue" in key -> "排队问题"
        "stairs" in key || "climb" in key -> "爬楼梯问题"
        "chicken" in key -> "鸡兔同笼"
        "circle" in key -> "圆圈间隔问题"
        "reverse" in key || "reorder" in key -> "倒推/凑整"
        "age" in key -> "年龄问题"
        "multiple" in key || "sum" in key -> "倍数问题"
        "clock" in key -> "认识钟表"
        "money" in key -> "人民币换算"
        "unit" in key -> "单位换算"
        "divide" in key || "remainder" in key -> "有余数除法"
        "multiply" in key -> "表内乘法"
        "pattern" in key || "sequence" in key -> "找规律"
        else -> "数学应用题"
    }

    // ===== 逐项掌握度（英语单词 / 三上必背共用）：项 -> 答对次数（不在表=全新；0..2=学习中；>=3=已掌握）=====
    private const val KEY_ENGLISH_MASTERY = "EnglishMastery"
    private const val KEY_RECITATION_MASTERY = "RecitationMastery"

    private fun loadMastery(prefs: SharedPreferences, prefKey: String): MutableMap<String, Int> {
        val m = mutableMapOf<String, Int>()
        val s = prefs.getString(prefKey, "{}") ?: "{}"
        try { val o = JSONObject(s); for (k in o.keys()) m[k] = o.getInt(k) } catch (e: Exception) {}
        return m
    }

    private fun saveMastery(prefs: SharedPreferences, prefKey: String, m: Map<String, Int>) {
        val o = JSONObject(); m.forEach { (k, v) -> o.put(k, v) }
        prefs.edit().putString(prefKey, o.toString()).apply()
    }

    // 答题后更新单词掌握度：答对 +1，答错清零
    fun recordEnglishResult(context: Context, word: String, isCorrect: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val m = loadMastery(prefs, KEY_ENGLISH_MASTERY)
        m[word] = if (isCorrect) (m[word] ?: 0) + 1 else 0
        saveMastery(prefs, KEY_ENGLISH_MASTERY, m)
    }

    // 图鉴用只读快照：掌握度 >=3 视为已点亮
    fun getMasteredEnglish(context: Context): Set<String> =
        loadMastery(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE), KEY_ENGLISH_MASTERY)
            .filterValues { it >= 3 }.keys

    fun getMasteredRecitation(context: Context): Set<String> =
        loadMastery(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE), KEY_RECITATION_MASTERY)
            .filterValues { it >= 3 }.keys

    // 三上必背掌握度：答对 +1，答错清零（清零后下次回到教学卡重新教）
    fun recordRecitationResult(context: Context, key: String, isCorrect: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val m = loadMastery(prefs, KEY_RECITATION_MASTERY)
        m[key] = if (isCorrect) (m[key] ?: 0) + 1 else 0
        saveMastery(prefs, KEY_RECITATION_MASTERY, m)
    }

    private val cloudQuestions = mutableListOf<Question>()
    private val errorRecords = mutableMapOf<String, Int>()
    private val lastSeenRound = mutableMapOf<String, Int>()       // 语文题上次出现的轮次号
    private val mathTypeSeenRound = mutableMapOf<String, Int>()          // 数学题型上次出现距今轮数
    private val mathTypeErrors = mutableMapOf<String, Int>()        // 数学题型答错次数

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

        lastSeenRound.clear()
        val lsr = prefs.getString(KEY_LAST_SEEN_ROUND, "{}") ?: "{}"
        try {
            val obj = JSONObject(lsr)
            for (key in obj.keys()) lastSeenRound[key] = obj.getInt(key)
        } catch (e: Exception) {}

        mathTypeSeenRound.clear()
        val mts = prefs.getString(KEY_MATH_TYPE_SEEN, "{}") ?: "{}"
        try {
            val obj = JSONObject(mts)
            for (key in obj.keys()) mathTypeSeenRound[key] = obj.getInt(key)
        } catch (e: Exception) {}

        mathTypeErrors.clear()
        val mte = prefs.getString(KEY_MATH_TYPE_ERRORS, "{}") ?: "{}"
        try {
            val obj = JSONObject(mte)
            for (key in obj.keys()) mathTypeErrors[key] = obj.getInt(key)
        } catch (e: Exception) {}
    }

    // 阅读理解：按难度档混出 —— 基础档以「照抄型」缓冲为主，进阶/挑战档以三年级推理题为主
    private fun generateReadingQuestion(): Question {
        val hardChance = when (currentDifficulty) { 1 -> 20; 3 -> 90; else -> 70 }
        return if (Random.nextInt(100) < hardChance) generateHardReading() else generateEasyReading()
    }

    // 三年级阅读：读完再算 / 代词指代 / 因果原因 / 词语理解+概括
    private fun generateHardReading(): Question {
        val names = listOf("小欣", "小明", "小红", "小华", "小丽", "小杰")
        return when (Random.nextInt(4)) {
            // A 读完再算（多步，口算友好）
            0 -> {
                val name = names.random()
                if (Random.nextBoolean()) {
                    val item = listOf("个苹果", "块糖", "张贴纸", "本练习册").random()
                    val a = Random.nextInt(12, 21); val b = Random.nextInt(2, 6); val c = Random.nextInt(2, 6)
                    createMathQ("${name}有 $a $item，先送给同桌 $b ，又用掉 $c 。\n\n问题：${name}现在还剩几$item？", a - b - c, "从总数里把送掉和用掉的都减去")
                } else {
                    val a = Random.nextInt(12, 31); val b = Random.nextInt(2, 9)
                    createMathQ("图书角有故事书 $a 本，科技书比故事书少 $b 本。\n\n问题：两种书一共有多少本？", a + (a - b), "先算出科技书有几本，再和故事书相加")
                }
            }
            // B 代词指代
            1 -> {
                if (Random.nextBoolean()) {
                    val animal = listOf("小鸟", "小猫", "小松鼠", "蝴蝶").random()
                    val place = listOf("树上", "屋檐下", "花丛里", "草地上").random()
                    createQuestion("$place 有一只$animal，它正在快活地玩耍。\n\n问题：句中的“它”指的是什么？", animal, listOf("树", "花", "屋檐").shuffled().take(3))
                } else {
                    val name = names.random()
                    val flower = listOf("一朵花", "一只蝴蝶", "一道彩虹").random()
                    val others = names.filter { it != name }.shuffled().take(2)
                    createQuestion("${name}看见路边有$flower，被深深吸引，停下来看了好久。\n\n问题：句中“被深深吸引”的是谁？", name, others + flower)
                }
            }
            // C 因果原因
            2 -> {
                val cases = listOf(
                    Triple("昨天下了一场大雪", "学校的运动会改到了下周", "运动会"),
                    Triple("小明发烧生病了", "今天没来上学", "小明今天"),
                    Triple("天气太热", "小狗躲到了大树的阴影下", "小狗"),
                    Triple("路上堵车了", "爸爸回家晚了半小时", "爸爸")
                )
                val p = cases.random()
                val wrongs = cases.filter { it.first != p.first }.map { "因为${it.first}" }.shuffled().take(3)
                createQuestion("因为${p.first}，所以${p.second}。\n\n问题：${p.third}为什么会这样？", "因为${p.first}", wrongs)
            }
            // D 词语理解 / 概括（随机二选一）
            else -> {
                if (Random.nextBoolean()) {
                    val words = listOf(
                        Triple("操场上鸦雀无声，同学们都在认真听讲", "鸦雀无声", "非常安静"),
                        Triple("听到放假的消息，大家兴高采烈", "兴高采烈", "非常高兴"),
                        Triple("他飞快地跑回了家", "飞快", "非常快"),
                        Triple("小红做作业很仔细，一个错都没有", "仔细", "认真细心")
                    )
                    val p = words.random()
                    val pool = listOf("非常安静", "非常高兴", "非常快", "认真细心", "非常生气", "非常难过")
                    val wrongs = pool.filter { it != p.third }.shuffled().take(3)
                    createQuestion("${p.first}。\n\n问题：“${p.second}”的意思是？", p.third, wrongs)
                } else {
                    val topics = listOf(
                        Triple("春天来了，小草绿了，桃花开了，燕子也从南方飞回来了。", "春天的景象", listOf("夏天很炎热", "秋天落叶了", "冬天下雪了")),
                        Triple("妈妈每天早起做饭，送我上学，晚上还陪我读书写字。", "妈妈很辛苦地照顾我", listOf("我很爱学习", "爸爸去上班了", "我会自己做饭")),
                        Triple("蜜蜂飞到花丛中采蜜，从早忙到晚，一刻也不停。", "蜜蜂很勤劳", listOf("花儿很漂亮", "蜜蜂在睡觉", "天气很好"))
                    )
                    val p = topics.random()
                    createQuestion("${p.first}\n\n问题：这段话主要写的是什么？", p.second, p.third)
                }
            }
        }
    }

    // 照抄型阅读（一二年级，作基础档缓冲）：姓名/数量/颜色/顺序/地点/天气每次随机
    private fun generateEasyReading(): Question {
        val names = listOf("小欣", "小明", "小红", "小华", "小丽", "小杰")
        return when (Random.nextInt(6)) {
            // ① 采果子——读数量
            0 -> {
                val name = names.random()
                val place = listOf("果园", "山上", "外婆家", "农场").random()
                val fruit = listOf("苹果", "桃子", "橘子", "梨", "草莓").random()
                val a = Random.nextInt(6, 16)
                val b = Random.nextInt(2, a - 1)
                val passage = "星期天，${name}去${place}玩。${name}一共采了 $a 个${fruit}，送给好朋友 $b 个。"
                if (Random.nextBoolean())
                    createQuestion("$passage\n\n问题：${name}一共采了几个${fruit}？", "$a", listOf("${a - 1}", "${a + 1}", "$b"))
                else
                    createQuestion("$passage\n\n问题：${name}送给好朋友几个${fruit}？", "$b", listOf("$a", "${b + 1}", "${b - 1}"))
            }
            // ② 新书包——读颜色 / 图案
            1 -> {
                val name = names.random()
                val colors = listOf("红", "黄", "蓝", "绿", "紫", "粉")
                val color = colors.random()
                val animals = listOf("小猫", "小狗", "小兔", "小熊", "小鸟")
                val animal = animals.random()
                val passage = "${name}有一个新书包，是${color}色的，书包上画着一只${animal}。"
                if (Random.nextBoolean())
                    createQuestion("$passage\n\n问题：${name}的书包是什么颜色的？", "${color}色", colors.filter { it != color }.shuffled().take(3).map { "${it}色" })
                else
                    createQuestion("$passage\n\n问题：书包上画着什么？", animal, animals.filter { it != animal }.shuffled().take(3))
            }
            // ③ 比多少——三人比较
            2 -> {
                val three = names.shuffled().take(3)
                val counts = (5..20).shuffled().take(3)
                val item = listOf("颗糖", "本书", "支铅笔", "张贴纸").random()
                val passage = "${three[0]}有 ${counts[0]} ${item}，${three[1]}有 ${counts[1]} ${item}，${three[2]}有 ${counts[2]} ${item}。"
                if (Random.nextBoolean()) {
                    val i = counts.indices.maxByOrNull { counts[it] }!!
                    createQuestion("$passage\n\n问题：谁的${item}最多？", three[i], three.filterIndexed { j, _ -> j != i })
                } else {
                    val i = counts.indices.minByOrNull { counts[it] }!!
                    createQuestion("$passage\n\n问题：谁的${item}最少？", three[i], three.filterIndexed { j, _ -> j != i })
                }
            }
            // ④ 事情顺序
            3 -> {
                val name = names.random()
                val acts = listOf("刷牙", "吃早饭", "背书包上学", "读故事书", "洗脸").shuffled().take(3)
                val passage = "早上，${name}先${acts[0]}，再${acts[1]}，最后${acts[2]}。"
                when (Random.nextInt(3)) {
                    0 -> createQuestion("$passage\n\n问题：${name}最先做什么？", acts[0], acts.drop(1))
                    1 -> createQuestion("$passage\n\n问题：${name}最后做什么？", acts[2], acts.dropLast(1))
                    else -> createQuestion("$passage\n\n问题：${name}第二件做的事是什么？", acts[1], listOf(acts[0], acts[2]))
                }
            }
            // ⑤ 小动物住处
            4 -> {
                val pairs = listOf(
                    Triple("小鱼", "河里", "游来游去"),
                    Triple("小鸟", "树上", "唱歌"),
                    Triple("小兔", "草地上", "吃萝卜"),
                    Triple("小蚂蚁", "地下", "搬粮食"),
                    Triple("小蜜蜂", "花丛里", "采蜜")
                )
                val p = pairs.random()
                val passage = "${p.first}住在${p.second}，每天在那里${p.third}。"
                if (Random.nextBoolean())
                    createQuestion("$passage\n\n问题：${p.first}住在哪里？", p.second, pairs.map { it.second }.filter { it != p.second }.shuffled().take(3))
                else
                    createQuestion("$passage\n\n问题：${p.first}每天在做什么？", p.third, pairs.map { it.third }.filter { it != p.third }.shuffled().take(3))
            }
            // ⑥ 天气与穿戴
            else -> {
                val name = names.random()
                val pairs = listOf(
                    Triple("下雨", "雨伞", "上学去"),
                    Triple("下雪", "手套", "堆雪人"),
                    Triple("天晴", "帽子", "去公园")
                )
                val p = pairs.random()
                val passage = "今天${p.first}了，${name}带上${p.second}，高高兴兴地${p.third}。"
                if (Random.nextBoolean())
                    createQuestion("$passage\n\n问题：今天天气怎么样？", p.first, pairs.map { it.first }.filter { it != p.first })
                else
                    createQuestion("$passage\n\n问题：${name}带了什么出门？", p.second, pairs.map { it.second }.filter { it != p.second })
            }
        }
    }

        // 动态难度追踪（持久化，跨答题局保留）
    private var currentDifficulty: Int = 2 // 1: 基础, 2: 进阶, 3: 挑战
    private var consecutiveCorrect: Int = 0

    private fun loadDifficulty(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        currentDifficulty = prefs.getInt(KEY_DIFFICULTY, 2).coerceIn(1, 3)
        consecutiveCorrect = prefs.getInt(KEY_CONSEC_CORRECT, 0)
    }

    // 答对连续 2 题升档，答错立即降档；结果持久化，让题目难度随孩子水平走
    fun updateDifficulty(context: Context, isCorrect: Boolean) {
        loadDifficulty(context)
        if (isCorrect) {
            consecutiveCorrect++
            if (consecutiveCorrect >= 2 && currentDifficulty < 3) {
                currentDifficulty++
                consecutiveCorrect = 0
            }
        } else {
            consecutiveCorrect = 0
            if (currentDifficulty > 1) {
                currentDifficulty--
            }
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt(KEY_DIFFICULTY, currentDifficulty)
            .putInt(KEY_CONSEC_CORRECT, consecutiveCorrect)
            .apply()
    }

    // --- 高阶思维训练：逆向思维数学（数字都收在口算范围，靠倒推思路而非硬算）---
    private fun generateReverseMath(): Question {
        return when (Random.nextInt(3)) {
            0 -> {
                // 一步减半：现在剩 count 个 = 原来的一半，原来 = count × 2（口算）
                val count = Random.nextInt(3, 13)
                val start = count * 2
                createQuestion("小欣有一些松果，吃掉了一半后还剩 $count 个，她原来有多少个？", "$start",
                    listOf("$count", "${count + 2}", "${start + 2}"),
                    "剩下的是一半，把它加倍（×2）就是原来的")
            }
            1 -> {
                val remain = Random.nextInt(5, 15)
                val lastSpent = Random.nextInt(2, 8)
                val start = (remain + lastSpent) * 2
                createQuestion("小欣买文具，先花了一半钱，又用了 $lastSpent 元，最后剩下 $remain 元，她原本有多少钱？", "$start",
                    listOf("${start / 2}", "${remain + lastSpent}", "${start + 10}"),
                    "倒推：先把最后剩的和又用的加起来，再×2（那是一半）")
            }
            else -> {
                val restCount = Random.nextInt(5, 12)
                val startFloor = restCount + 1
                createQuestion("小欣下楼梯，每下一层都要休息一下，下到第 1 层时恰好休息了 $restCount 次，她从第几层开始下的？", "$startFloor",
                    listOf("$restCount", "${restCount + 2}", "第 1 层"),
                    "下一层休息一次，休息 $restCount 次就下了 $restCount 层，再加上停的那层")
            }
        }
    }

    // --- 高阶思维训练：语境逻辑纠错 ---
    private fun generateLogicCorrection(): Question {
        return when (Random.nextInt(3)) {
            0 -> createQuestion("“因为今天天气很热，所以我坚持去操场跑步。” 这句话哪里逻辑有问题？", "“因为...所以”不能用于转折关系", listOf("没有问题", "跑步不应该在操场", "天气热不能跑步"))
            1 -> createQuestion("“小草变绿了，因为春天来了。” 请选出逻辑更自然的表述：", "因为春天来了，所以小草变绿了", listOf("春天来了，所以小草变绿了", "小草变绿了，因为春天来了", "因为小草变绿了，所以春天来了"))
            else -> createQuestion("“家里有：苹果、香蕉、西瓜、水果。” 哪一个词不是同一类？", "水果", listOf("苹果", "香蕉", "西瓜"))
        }
    }

    // --- 趣味谜语 / 脑筋急转弯（激发兴趣，二年级适龄）---
    private fun generateFunRiddle(): Question {
        val items = listOf(
            Triple("🍉 身穿绿衣裳，肚里水汪汪，子儿多又多，个个黑脸膛。（打一水果）", "西瓜", listOf("苹果", "葡萄", "香蕉")),
            Triple("🥜 麻屋子，红帐子，里面住着白胖子。（打一食物）", "花生", listOf("核桃", "栗子", "玉米")),
            Triple("☀️ 一个老公公，面孔红彤彤，晚上不见面，白天来上工。（打一自然现象）", "太阳", listOf("月亮", "星星", "彩虹")),
            Triple("🌧️ 千条线，万条线，落到水里看不见。（打一自然现象）", "雨", listOf("雪", "风", "云")),
            Triple("🤔 什么东西越洗越脏？", "水", listOf("衣服", "手帕", "碗")),
            Triple("🚗 什么车没有轮子也能转？", "风车", listOf("汽车", "马车", "自行车")),
            Triple("📅 一年中哪几个月有28天？", "每个月都有", listOf("只有2月", "只有1月", "一个也没有")),
            Triple("👦 爸爸有三个儿子，大儿子叫大毛，二儿子叫二毛，三儿子叫什么？", "小欣", listOf("三毛", "小毛", "毛毛")),
            Triple("🐴 白色的马叫白马，会拉车的马叫什么？", "马车的马", listOf("斑马", "木马", "河马")),
            Triple("🌡️ 什么东西天气越热，它爬得越高？", "温度计", listOf("小猫", "气球", "树叶")),
            Triple("🐔 先有鸡还是先有蛋，鸡是从哪里出来的？", "蛋", listOf("鸡妈妈", "天上", "土里")),
            Triple("🌙 什么时候太阳会从西边出来？", "永远不会", listOf("夏天", "冬天", "下雨天"))
        )
        val it = items.random()
        return createQuestion(it.first, it.second, it.third)
    }

    // --- 二年级语文重点考点生成器 ---
    private fun generateAcademicChineseQuestion(): Question {
        return when (Random.nextInt(5)) {
            0 -> { // 形近字
                val items = listOf("带、戴" to "带领", "园、圆" to "公园", "锋、峰" to "锋利", "墓、慕" to "扫墓")
                val item = items.random()
                val parts = item.first.split("、")
                createQuestion("请选出正确的字填入括号：${item.second}( )", parts[0], listOf(parts[1], "提", "立"))
            }
            1 -> { // 多音字
                val items = listOf("得" to "得到(dé)", "为" to "成为(wéi)", "发" to "发现(fā)", "倒" to "摔倒(dǎo)")
                val item = items.random()
                createQuestion("加点字“${item.first}”在“${item.second}”中读音正确吗？", "正确", listOf("错误", "不确定"))
            }
            2 -> { // 成语填空
                val items = listOf("山清( )秀" to "水", "名胜( )迹" to "古", "五光( )色" to "十", "春暖( )开" to "花")
                val item = items.random()
                createQuestion("补全成语：${item.first.replace("(", "（ ）")}", item.second, listOf("土", "月", "日"))
            }
            3 -> { // 古诗
                val poems = listOf("欲穷千里目，( )" to "更上一层楼", "天苍苍，野茫茫。( )" to "风吹草低见牛羊", "危楼高百尺，( )" to "手可摘星辰")
                val p = poems.random()
                createQuestion("填出古诗下一句：${p.first.replace("(", "（ ）")}", p.second, listOf("白日依山尽", "忙趁东风放纸鸢", "遥知不是雪"))
            }
            else -> { // 名言
                createQuestion("与其锦上添花，不如（ ）。", "雪中送炭", listOf("锦上添花", "雪上加霜", "置之不理"))
            }
        }
    }

    // 旧数学题子类型名称
    private val grade2TypeNames = listOf(
        "grade2_add_sub", "grade2_multiply", "grade2_divide_rem", "grade2_weight",
        "grade2_motion", "grade2_digits", "grade2_mix"
    )
    private val advancedTypeNames = listOf(
        "adv_queue_left_right", "adv_queue_total", "adv_tree_planting", "adv_saw_log",
        "adv_climb_stairs", "adv_circle_flower", "adv_age_diff", "adv_age_sum",
        "adv_unit_convert", "adv_boat_rent", "adv_buy_notebook", "adv_color_pattern",
        "adv_approximate", "adv_multiply_compare", "adv_multiple_sum", "adv_basket_balls",
        "adv_age_multiple",
        // 新增 10 种
        "adv_clock_reading", "adv_time_calc", "adv_directions", "adv_money_convert",
        "adv_combination", "adv_digit_puzzle", "adv_statistics", "adv_observe_3d",
        "adv_simple_equation", "adv_remainder_app"
    )
    fun getRandomQuestions(context: Context, count: Int): List<Question> {
        loadLocalData(context)
        loadDifficulty(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentRound = prefs.getInt(KEY_QUIZ_ROUND, 0)
        val allVerbalPool = (cloudQuestions + builtinVerbalQuestions + ThinkingChineseQuestions.questions).distinctBy { it.text }
        val selectedQuestions = mutableSetOf<Question>()

        // 同感题型每局限流：找规律（3个题源冷却互不相通）和连词成句都最多 1 道
        fun canAdd(q: Question): Boolean {
            if (selectedQuestions.any { it.text == q.text }) return false
            if ("规律" in q.text && selectedQuestions.any { "规律" in it.text }) return false
            if ("通顺" in q.text && selectedQuestions.any { "通顺" in it.text }) return false
            return true
        }

        // 题量分配：末位留给 BOSS 难题，其余英语少量起步(1~2题)、语文/数学对半
        val target = count - 1
        val englishCount = if (count >= 8) 2 else 1
        val verbalLimit = (target - englishCount) / 2

        // 0. 三上必背（暑期预习，先教后测）：每局固定 2 道，计入语文份额
        val recitationMastery = loadMastery(prefs, KEY_RECITATION_MASTERY)
        var guard = 0
        while (selectedQuestions.count { it.masteryKey != null } < 2 && guard < 30) {
            guard++
            val q = Grade3Recitation.generateWeighted(recitationMastery)
            if (selectedQuestions.none { it.text == q.text }) {
                selectedQuestions.add(q)
                val k = Grade3Recitation.lastKey
                if (k.isNotEmpty() && !recitationMastery.containsKey(k)) recitationMastery[k] = 0  // 引入新内容（先教后测）
            }
        }
        saveMastery(prefs, KEY_RECITATION_MASTERY, recitationMastery)

        // 1. 动态语文题（仿写/标点/阅读/逻辑/课内考点），约占语文的 40%
        val dynamicVerbalTarget = maxOf(1, (verbalLimit * 0.4).toInt()) + selectedQuestions.size
        guard = 0
        while (selectedQuestions.size < dynamicVerbalTarget && guard < 80) {
            guard++
            val q = when(Random.nextInt(8)) {
                0 -> generateCompositionQuestion()
                1 -> generateVerbalLogicQuestion()
                2 -> generateAcademicChineseQuestion()   // 形近字/多音字/成语/古诗/名言（人教版二年级重点）
                3 -> generateFunRiddle()                 // 谜语/脑筋急转弯（趣味）
                else -> generateReadingQuestion()
            }
            if (canAdd(q)) selectedQuestions.add(q)
        }

        // 2. 其余语文 — 按“多久没出现 + 错过几次”加权
        val weightedPool = allVerbalPool.filter { q -> selectedQuestions.none { it.text == q.text } }
            .map { q ->
                val lastSeen = lastSeenRound[q.text] ?: 0
                val missed = currentRound - lastSeen
                val errs = errorRecords[q.text] ?: 0
                Pair(q, missed * 2 + errs * 5 + 1)
            }
            .toMutableList()

        while (selectedQuestions.size < verbalLimit && weightedPool.isNotEmpty()) {
            val totalWeight = weightedPool.sumOf { it.second }
            var r = Random.nextInt(totalWeight)
            for (i in weightedPool.indices) {
                r -= weightedPool[i].second
                if (r < 0) {
                    val q = weightedPool[i].first.shuffledOptions()
                    if (canAdd(q)) selectedQuestions.add(q)   // 不满足限流：只移出池子，防死循环
                    weightedPool.removeAt(i)
                    break
                }
            }
        }

        // 3. 英语启蒙（少量起步），按题型加权避免重复
        var englishAdded = 0
        guard = 0
        val englishMastery = loadMastery(prefs, KEY_ENGLISH_MASTERY)
        while (englishAdded < englishCount && selectedQuestions.size < target && guard < 50) {
            guard++
            val q = EnglishGenerator.generateWeighted(englishMastery)
            // 听力题按单词去重（允许两道不同单词的听力题），其余按题干去重
            val dup = selectedQuestions.any {
                if (q.audioWord != null) it.audioWord == q.audioWord else it.text == q.text
            }
            if (!dup) {
                selectedQuestions.add(q)
                if (EnglishGenerator.lastGeneratedType.isNotEmpty())
                    mathTypeSeenRound[EnglishGenerator.lastGeneratedType] = currentRound + 1
                val w = EnglishGenerator.lastWord
                if (w.isNotEmpty() && !englishMastery.containsKey(w)) englishMastery[w] = 0  // 引入新词（先教后测）
                englishAdded++
            }
        }
        saveMastery(prefs, KEY_ENGLISH_MASTERY, englishMastery)

        // 4. 数学 — 难度决定“奥数/思维题 : 课内题”的比例
        //    基础档(1)以课内为主，挑战档(3)奥数思维题更多，随孩子水平走
        val mathNeeded = target - selectedQuestions.size
        val challengeRatio = when (currentDifficulty) { 1 -> 0.2; 3 -> 0.7; else -> 0.45 }
        val challengeQuota = Math.round(mathNeeded * challengeRatio).toInt()
        var challengeAdded = 0
        guard = 0
        while (selectedQuestions.size < target && challengeAdded < challengeQuota && guard < 80) {
            guard++
            // 思维题 / 奥数题 / 巧算题 三选一轮换
            val src = challengeAdded % 3
            val q = when (src) {
                0 -> ThinkingMathGenerator.generateWeighted(mathTypeSeenRound, mathTypeErrors)
                1 -> OlympiadMathGenerator.generateWeighted(mathTypeSeenRound, mathTypeErrors)
                else -> SmartCalcGenerator.generateWeighted(mathTypeSeenRound, mathTypeErrors)
            }
            if (canAdd(q)) {
                selectedQuestions.add(q)
                val tn = when (src) {
                    0 -> ThinkingMathGenerator.lastGeneratedType
                    1 -> OlympiadMathGenerator.lastGeneratedType
                    else -> SmartCalcGenerator.lastGeneratedType
                }
                if (tn.isNotEmpty()) mathTypeSeenRound[tn] = currentRound + 1
                challengeAdded++
            }
        }

        // 课内数学题填满剩余（基础档优先纯口算课内题）
        guard = 0
        while (selectedQuestions.size < target && guard < 100) {
            guard++
            if (Random.nextInt(100) < 30) {              // 约 30% 出“手输得数”题，降低蒙对率（③）
                val q = generateInputMath()
                if (canAdd(q)) selectedQuestions.add(q)
                continue
            }
            val (q, typeName) = if (currentDifficulty == 1) generateGrade2Math() else selectOldMathByWeight()
            if (canAdd(q)) {
                selectedQuestions.add(q)
                mathTypeSeenRound[typeName] = currentRound + 1
            }
        }

        // 更新：把本局抽中的语文题 lastSeenRound = currentRound + 1
        val selectedTexts = selectedQuestions.map { it.text }.toSet()
        for (text in selectedTexts) {
            if (isStaticQuestion(text)) {
                lastSeenRound[text] = currentRound + 1
            }
        }
        // 连词成句共享冷却：出过任意一道，整组 10 道一起冷却（题感完全相同，逐题冷却等于每局都见）
        if (selectedTexts.any { "排列成通顺" in it }) {
            ThinkingChineseQuestions.questions.filter { "排列成通顺" in it.text }
                .forEach { lastSeenRound[it.text] = currentRound + 1 }
        }
        // 数学题型 - ThinkingMath 和 Olympiad 已在加权选择中记录
        if (ThinkingMathGenerator.lastGeneratedType.isNotEmpty()) {
            mathTypeSeenRound[ThinkingMathGenerator.lastGeneratedType] = currentRound + 1
        }
        if (OlympiadMathGenerator.lastGeneratedType.isNotEmpty()) {
            mathTypeSeenRound[OlympiadMathGenerator.lastGeneratedType] = currentRound + 1
        }
        // 旧数学题已在加权选择循环中记录子类型轮次

        // 持久化
        val lsObj = JSONObject()
        lastSeenRound.forEach { (k, v) -> lsObj.put(k, v) }
        val mtObj = JSONObject()
        mathTypeSeenRound.forEach { (k, v) -> mtObj.put(k, v) }
        val meObj = JSONObject()
        mathTypeErrors.forEach { (k, v) -> if (v > 0) meObj.put(k, v) }
        prefs.edit()
            .putString(KEY_LAST_SEEN_ROUND, lsObj.toString())
            .putString(KEY_MATH_TYPE_SEEN, mtObj.toString())
            .putString(KEY_MATH_TYPE_ERRORS, meObj.toString())
            .putInt(KEY_QUIZ_ROUND, currentRound + 1)
            .apply()

        // BOSS 题：末位固定一道思维/奥数档难题，配得上"🐉 大魔王"（显示层横幅在 FloatingService）
        var boss: Question? = null
        var bossGuard = 0
        while (boss == null && bossGuard < 10) {
            bossGuard++
            val q = if (Random.nextBoolean()) ThinkingMathGenerator.generateWeighted(mathTypeSeenRound, mathTypeErrors)
                else OlympiadMathGenerator.generateWeighted(mathTypeSeenRound, mathTypeErrors)
            if (canAdd(q)) boss = q
        }
        return selectedQuestions.toList().shuffled() + listOfNotNull(boss)
    }

    private fun isStaticQuestion(text: String): Boolean {
        return (builtinVerbalQuestions + ThinkingChineseQuestions.questions).any { it.text == text }
    }

    private fun selectOldMathByWeight(): Pair<Question, String> {
        val allOldTypes = grade2TypeNames.map { "old-$it" } + advancedTypeNames.map { "old-$it" }
        val maxRound = mathTypeSeenRound.values.maxOrNull() ?: 0
        val weights: List<Int> = allOldTypes.map { tn ->
            val lastSeen = mathTypeSeenRound[tn] ?: 0
            val missed = if (lastSeen > 0) maxRound - lastSeen else maxRound + 1
            val errs = mathTypeErrors[tn] ?: 0
            missed * 2 + errs * 5 + 1
        }
        val total: Int = weights.sum()
        var r: Int = Random.nextInt(total)
        var idx = 0
        for (w in weights) {
            r = r - w
            if (r < 0) break
            idx++
        }
        return if (idx < grade2TypeNames.size) {
            generateGrade2Math(idx)
        } else {
            generateAdvancedMathQuestion(idx - grade2TypeNames.size)
        }
    }

    private fun generateAdvancedMathQuestion(typeIdx: Int = -1): Pair<Question, String> {
        val idx = if (typeIdx >= 0) typeIdx else Random.nextInt(27)
        val q = when (idx) {
        0 -> { val a = Random.nextInt(3, 12); val b = Random.nextInt(3, 12); createMathQ("小朋友排队，小欣从左数第 $a 个，从右数第 $b 个，这一排共有多少人？", a + b - 1, "从两边数，小欣被数了两次，记得减 1") }
        1 -> { val total = Random.nextInt(15, 30); val a = Random.nextInt(5, 12); createMathQ("一排共有 $total 个小朋友，小欣从左边数是第 $a 个，从右数她是第几个？", total - a + 1, "从右数的位置 = 总人数 − 从左数的位置 + 1") }
        2 -> { val dist = Random.nextInt(3, 8); val gap = Random.nextInt(2, 5); createMathQ("在一条长 ${dist * gap} 米的小路一边种树，每隔 $gap 米种一棵（两端都种），共需多少棵？", dist + 1, "两端都种时，棵数比间隔数多 1") }
        3 -> { val pieces = Random.nextInt(3, 8); val perCut = Random.nextInt(2, 6); createMathQ("把一根木头锯成 $pieces 段，每锯一次需要 $perCut 分钟，一共需要多少分钟？", (pieces - 1) * perCut, "锯成 n 段只锯了 (n−1) 次，先算锯几次") }
        4 -> {
            val floorsPerSegment = Random.nextInt(2, 4) // 每一段的楼层数
            val timePerFloor = Random.nextInt(3, 7)      // 每层秒数取小，结果压在两位数
            val floorA = 1 + floorsPerSegment
            val timeA = floorsPerSegment * timePerFloor
            val floorB = floorA + Random.nextInt(2, 4)
            val timeB = (floorB - 1) * timePerFloor
            createMathQ("小欣从 1 楼爬到 $floorA 楼用了 $timeA 秒，以同样的速度爬到 $floorB 楼需要多少秒？", timeB,
                "从 1 楼到 n 楼其实只爬了 (n−1) 层，先算爬一层几秒")
        }
        5 -> { val count = Random.nextInt(5, 12); createMathQ("$count 个小朋友围成一个圆圈玩游戏，每两个小朋友之间放一盆花，一共需要多少盆花？", count, "围成一圈时，花的盆数和人数一样多") }
        6 -> { val dad = Random.nextInt(30, 45); val son = Random.nextInt(5, 12); val years = Random.nextInt(3, 20); createMathQ("爸爸今年 $dad 岁，小欣 $son 岁。$years 年后，爸爸比小欣大多少岁？", dad - son, "两人一起长大，年龄差永远不变") }
        7 -> { val sum = Random.nextInt(30, 50); val years = Random.nextInt(2, 6); createMathQ("今年爸爸和小欣的年龄和是 $sum 岁，$years 年后，他们的年龄和是多少岁？", sum + years * 2, "每过 1 年两人各长 1 岁，年龄和增加 2") }
        8 -> { val m = Random.nextInt(1, 5); val cm = Random.nextInt(10, 90); if (Random.nextBoolean()) createMathQ("$m 米 $cm 厘米 + ${100 - cm} 厘米 = ( ) 米", m + 1, "1 米 = 100 厘米，先把厘米凑成整米") else createMathQ("${m * 100 + cm} 厘米 - $m 米 = ( ) 厘米", cm, "1 米 = 100 厘米，先统一单位再减") }
        9 -> { val total = Random.nextInt(21, 35); val cap = Random.nextInt(4, 7); val ans = (total + cap - 1) / cap; createQuestion("$total 个小朋友去划船，每条船限坐 $cap 人，至少要租 ( ) 条船。", "$ans", listOf("${ans - 1}", "${ans + 1}", "${total / cap}"), "除完若还剩下人，要多租一条船装他们") }
        10 -> { val money = Random.nextInt(20, 40); val price = Random.nextInt(3, 7); val ans = money / price; createQuestion("小明有 $money 元钱，买 $price 元一个的本子，最多可以买 ( ) 个。", "$ans", listOf("${ans + 1}", "${ans - 1}", "${ans + 2}"), "看这些钱里最多包含几个单价") }
        11 -> { 
            val allColors = listOf("红", "黄", "蓝", "绿", "紫", "粉")
            val n = Random.nextInt(3, 5) // 随机生成 3 或 4 个颜色
            val colors = allColors.shuffled().take(n)
            val target = Random.nextInt(10, 25)
            val ans = colors[(target - 1) % n]
            val wrongs = colors.filter { it != ans }
            val patternDesc = colors.joinToString("、") + "..."
            createQuestion("按照“$patternDesc”的规律排列，第 $target 个是 ( ) 色。", ans, wrongs, "几种颜色一组循环，看第 $target 个落在一组里的第几位")
        }
        12 -> { val num = Random.nextInt(3001, 9999); val ans = ((num + 500) / 1000) * 1000; createQuestion("$num 的近似数是 ( )", "$ans", listOf("${ans - 1000}", "${ans + 1000}", "${ans - 500}"), "看百位：满 500 向千位进 1，不满就舍去") }
        13 -> { val a = Random.nextInt(3, 7); val b = Random.nextInt(3, 7); val left = a * b; val right = (a + 1) * (b - 1); val op = if (left > right) ">" else if (left < right) "<" else "="; createQuestion("$a × $b [ ] ${a + 1} × ${b - 1}", op, listOf(">", "<", "=").filter { it != op }, "两边分别先算出得数，再比大小") }
        14 -> { val m = Random.nextInt(3, 8); val n = Random.nextInt(2, 4); createMathQ("小明有 $m 个苹果，小红的苹果数是小明的 $n 倍，两人共有多少个苹果？", m * (n + 1), "先想两人一共是几份，再乘每份的数") }
        15 -> { val yellow = Random.nextInt(3, 8); val n = Random.nextInt(3, 6); createMathQ("筐里有红球和黄球，黄球有 $yellow 个，红球数量是黄球的 $n 倍，红球有多少个？", yellow * n, "红球 = 黄球 × 倍数") }
        16 -> { val n = Random.nextInt(3, 5); val son = if (n == 3) Random.nextInt(10, 13) else Random.nextInt(7, 11); val dad = son * n; createMathQ("今年爸爸 $dad 岁，小欣 $son 岁，爸爸的年龄是小欣的多少倍？", n, "看爸爸的年龄里面有几个小欣的年龄") }
        // 新增 10 种题型 (idx 17-26)
        17 -> { val h = Random.nextInt(1, 12); val m_choices = listOf(0, 15, 30, 45); val m = m_choices.random(); val m_str = if (m == 0) "12" else if (m == 15) "3" else if (m == 30) "6" else "9"; createQuestion("钟面上时针指向 $h，分针指向 $m_str，现在是 ( )", "$h:${if (m == 0) "00" else "$m"}", listOf("$h:${if (m == 0) "30" else "00"}", "${if (h < 12) h + 1 else 1}:00", "$h:55"), "时针指几就是几点，分针指的位置换成分钟") }
        18 -> { val start_h = Random.nextInt(7, 10); val mins = listOf(15, 30, 45, 60).random(); val end_h = start_h + mins / 60; val end_m = mins % 60; val end_m_str = if (end_m == 0) "00" else "$end_m"; createQuestion("小欣 $start_h:00 开始写作业，写了 $mins 分钟，( ) 写完。", "$end_h:$end_m_str", listOf("${if (end_h > 1) end_h - 1 else end_h}:00", "${end_h + 1}:00", "$end_h:${(end_m + 10) % 60}"), "从开始时间往后数经过的分钟") }
        19 -> { val face: String = listOf("北", "南", "东", "西").random(); val back = mapOf("北" to "南", "南" to "北", "东" to "西", "西" to "东")[face]!!; val allDirs: List<String> = listOf("东", "西", "南", "北"); createQuestion("小明面向$face，他的后面是什么方向？", back, allDirs.filter { it != back }, "面向一个方向，后面就是它的相反方向") }
        20 -> { val yuan = Random.nextInt(1, 10); val jiao = listOf(5, 10, 50).random(); val total_jiao = yuan * 10 + jiao; createQuestion("$yuan 元 $jiao 角 = ( ) 角", "$total_jiao", listOf("${yuan * 10}", "${total_jiao + 5}", "${total_jiao - 10}"), "1 元 = 10 角，先把元换成角再相加") }
        21 -> { val n = Random.nextInt(2, 4); val total = n * (n - 1); val digitList = (1..n).joinToString("、"); createQuestion("用 $digitList 这${n}个数字可以组成 ( ) 个没有重复数字的两位数。", "$total", listOf("${total - 1}", "${total + 1}", "${total + 2}"), "每个数字都能当十位，十位定了个位还剩几种选") }
        22 -> { val total = Random.nextInt(20, 80); val b = Random.nextInt(5, total - 4); val a = total - b; createMathQ("（ ）+ $b = $total，括号里应该填几？", a, "用总数减去已经知道的那个加数") }
        23 -> { val a = Random.nextInt(8, 15); createQuestion("小明跳了 $a 下，小红比小明多跳 3 下，小兰比小红多跳 2 下，小兰跳了 ( ) 下。", "${a + 5}", listOf("${a + 3}", "${a + 2}", "${a + 4}"), "一层层往上加：先算小红，再算小兰") }
        24 -> { val shapes = listOf(Triple("正方体", "6", listOf("4", "5", "8")), Triple("长方体", "6", listOf("4", "5", "8")), Triple("圆柱", "3", listOf("2", "4", "6"))); val s = shapes.random(); createQuestion("${s.first}有几个面？", s.second, s.third, "闭上眼睛想一想这个立体图形有几个面") }
        25 -> { val x = Random.nextInt(5, 20); val add = Random.nextInt(10, 30); val total = x + add; createQuestion("一个数加上 $add 等于 $total，这个数是 ( )", "$x", listOf("${x + 1}", "${x + 2}", "${x - 1}"), "用结果减去加上去的那个数") }
        26 -> { val total_apples = Random.nextInt(10, 30); val kids = Random.nextInt(3, 7); val quotient = total_apples / kids; val rem = total_apples % kids; createQuestion("$total_apples 个苹果平均分给 $kids 个小朋友，每人 ${quotient} 个，还剩 ( ) 个。", "$rem", listOf("${if (rem > 0) rem - 1 else 1}", "${rem + 1}", "${rem + 2}"), "分完后剩下的，一定比小朋友的人数少") }
        else -> { val n = Random.nextInt(3, 5); val son = if (n == 3) Random.nextInt(10, 13) else Random.nextInt(7, 11); val dad = son * n; createMathQ("今年爸爸 $dad 岁，小欣 $son 岁，爸爸的年龄是小欣的多少倍？", n, "看爸爸的年龄里面有几个小欣的年龄") }
        }
        val typeName = if (idx < 27) "old-" + advancedTypeNames[idx] else "old-" + advancedTypeNames[26]
        return Pair(q, typeName)
    }

    private fun generateVerbalLogicQuestion(): Question = when (Random.nextInt(10)) {
        0 -> { val categories = listOf(listOf("苹果", "香蕉", "西瓜", "青菜"), listOf("老虎", "狮子", "灰狼", "菊花"), listOf("铅笔", "书包", "尺子", "雨鞋"), listOf("燕子", "喜鹊", "大雁", "松鼠"), listOf("白云", "星星", "太阳", "操场"), listOf("跳高", "跑步", "打球", "读书")); val cat = categories.random(); createQuestion("找出不是同一类的词：", cat.last(), cat.dropLast(1)) }
        1 -> { val items = listOf(Triple("日", "月", "明"), Triple("女", "马", "妈"), Triple("人", "木", "休"), Triple("口", "十", "叶"), Triple("门", "口", "问"), Triple("木", "木", "林"), Triple("小", "大", "尖"), Triple("口", "天", "吴"), Triple("立", "占", "站"), Triple("讠", "也", "说")); val item = items.random(); val allWrongs = "好男认写字校学们位明".map { it.toString() }.filter { it != item.third }; createQuestion("${item.first} + ${item.second} = ( )", item.third, allWrongs.shuffled().take(3)) }
        2 -> { val m = listOf("一( )画" to "幅", "一( )马" to "匹", "一( )雷声" to "声", "一( )小路" to "条").random(); createQuestion(m.first, m.second, listOf("个", "只", "片")) }
        3 -> { val r = listOf("“春天像个害羞的小姑娘”是( )句" to "比喻", "“小树在风中点头”是( )句" to "拟人").random(); createQuestion(r.first, r.second, listOf("夸张", "排比", "反问").filter { it != r.second }) }
        4 -> { val c = listOf("端午节吃( )" to "粽子", "元宵节吃( )" to "元宵", "春节是( )的开始" to "一年").random(); createQuestion(c.first, c.second, listOf("月饼", "饺子", "春分")) }
        5 -> { val y = listOf("《亡羊补牢》告诉我们要( )" to "及时改正错误", "《揠苗助长》告诉我们不能( )" to "急于求成").random(); createQuestion(y.first, y.second, listOf("努力学习", "尊敬师长", "勤俭节约").filter { it != y.second }) }
        // 字谜
        6 -> { val z = listOf(
            Triple("一口咬掉牛尾巴（打一字）", "告", listOf("生", "失", "年")),
            Triple("二人坐在土堆上（打一字）", "坐", listOf("丛", "尘", "坎")),
            Triple("山上还有山（打一字）", "出", listOf("岳", "峰", "岭")),
            Triple("门里站着一个人（打一字）", "闪", listOf("间", "闭", "问")),
            Triple("三人同日来，喜见百花开（打一字）", "春", listOf("夏", "秦", "奉")),
            Triple("十张口（打一字）", "古", listOf("叶", "田", "回"))
        ).random(); createQuestion(z.first, z.second, z.third) }
        // 词语类比
        7 -> { val a = listOf(
            listOf("医生", "医院", "老师", "学校", "学生", "教室", "黑板"),
            listOf("司机", "汽车", "飞行员", "飞机", "机场", "天空", "翅膀"),
            listOf("鱼", "水", "鸟", "天空", "树林", "翅膀", "笼子"),
            listOf("笔", "写字", "刀", "切东西", "吃饭", "剪纸", "画画"),
            listOf("太阳", "白天", "月亮", "夜晚", "星星", "灯光", "中午")
        ).random(); createQuestion("${a[0]}对${a[1]}，就像${a[2]}对（ ）", a[3], a.subList(4, 7)) }
        // 词语接龙
        8 -> { val z = listOf(
            Triple("开心", "心情", listOf("高兴", "难过", "快乐")),
            Triple("学习", "习惯", listOf("读书", "作业", "老师")),
            Triple("朋友", "友谊", listOf("同学", "伙伴", "好人")),
            Triple("春天", "天空", listOf("夏天", "下雨", "花朵")),
            Triple("白云", "云朵", listOf("蓝天", "下雨", "晴空"))
        ).random(); createQuestion("词语接龙：${z.first} →（ ）？选出能接上的词", z.second, z.third) }
        // 一词多义
        else -> { val z = listOf(
            listOf("夜深了，大家都睡了。", "深", "时间晚、久", "距离大", "颜色浓", "感情厚"),
            listOf("我老去外婆家玩。", "老", "经常", "年纪大", "不新鲜", "排行最后"),
            listOf("妈妈在打电话。", "打", "拨打、进行", "用手击", "买东西", "编织"),
            listOf("买东西花了十元。", "花", "用掉、消费", "花朵", "花纹", "眼花"),
            listOf("水管跑水了。", "跑", "漏出来", "奔跑", "逃走", "旅行")
        ).random(); createQuestion("“${z[1]}”在“${z[0]}”中的意思是？", z[2], z.subList(3, 6)) }
    }

    private fun generateCompositionQuestion(): Question = when (Random.nextInt(11)) {
        0 -> { val items = mapOf("小草" to "绿油油", "太阳" to "红彤彤", "稻田" to "金灿灿", "白云" to "白茫茫", "黑头发" to "乌溜溜"); val noun = items.keys.random(); createQuestion("( )的$noun", items[noun]!!, listOf("亮晶晶", "水汪汪", "胖乎乎", "静悄悄").filter { it != items[noun]!! }.shuffled().take(3)) }
        1 -> { val pairs = listOf(Triple("因为", "所以", "下雨了" to "我们不去公园"), Triple("因为", "所以", "生病了" to "请假在家休息"), Triple("虽然", "但是", "天气很热" to "他坚持跑步"), Triple("虽然", "但是", "题目很难" to "我做出来了"), Triple("不但", "而且", "这朵花很美" to "散发着清香"), Triple("不但", "而且", "小明爱学习" to "爱劳动")).random(); createQuestion("( ) ${pairs.third.first}，( ) ${pairs.third.second}。", "${pairs.first}...${pairs.second}", listOf("如果...就", "只要...就", "一边...一边")) }
        2 -> { val m = listOf(Triple("弯弯的月儿像", "小船", listOf("圆盘", "大树", "星星")), Triple("圆圆的荷叶像", "大伞", listOf("小路", "小船", "石头")), Triple("闪闪的星星像", "眼睛", listOf("火球", "珍珠", "大山")), Triple("红红的枫叶像", "手掌", listOf("小鱼", "邮票", "云朵"))).random(); createQuestion("${m.first}( )", m.second, m.third) }
        3 -> { val s = listOf(Triple("小明写完作业了吗", "？", listOf("。", "！", "，")), Triple("这朵花开得真美呀", "！", listOf("。", "？", "，")), Triple("小欣正在操场上跑步", "。", listOf("？", "！", "，")), Triple("你能帮我个忙吗", "？", listOf("。", "！", "，"))).random(); createQuestion("${s.first}( )", s.second, s.third) }
        4 -> { val s = listOf(Triple("小狗（ ）到门口，（ ）着尾巴。", "跑/摇", listOf("看/咬", "跳/睡", "走/摆")), Triple("小明（ ）起书包，（ ）向学校。", "背/跑", listOf("提/走", "抱/看", "拿/飞")), Triple("李阿姨（ ）下腰，（ ）起地上的纸屑。", "弯/捡", listOf("站/看", "坐/拿", "直/丢"))).random(); createQuestion(s.first, s.second, s.third) }
        5 -> { val b = listOf("小欣在读书。" to "小欣正在认真地读一本有趣的书。", "小明在画画。" to "小明正在用心地画一幅美丽的画。").random(); createQuestion("怎样把“${b.first}”写得更生动？", b.second, listOf("小欣读了很多书。", "小欣在房间读书。", "小明画画很好看。")) }
        6 -> { val n = listOf("小欣", "小明").random(); val p = listOf("教室" to listOf("打扫了", "整理了", "擦干净了"), "书本" to listOf("整理了", "拿走了", "放好了"), "苹果" to listOf("洗好了", "吃掉了", "削皮了")).random(); val act = p.second.random(); val obj = p.first; createQuestion("把“$n$act$obj。”改成“被”字句：", "${obj}被${n}${act}。", listOf("${obj}把${n}${act}。", "${n}把${obj}${act}。", "${n}${act}了${obj}。")) }
        7 -> { val s = listOf(listOf("小欣", "在", "认真地", "写作业"), listOf("小明", "在", "开心地", "踢足球")).random(); val correct = s.joinToString("") + "。"; createQuestion("下面哪组词语可以排成一句通顺的话？", correct, listOf(s.reversed().joinToString("") + "。", "我${s[0]}很${s[2]}。", "${s[0]}${s[2]}${s[1]}${s[3]}。")) }
        8 -> { if (Random.nextBoolean()) createQuestion("下列哪个词语是 AABB 式的？", listOf("躲躲藏藏", "叮叮当当", "欢欢喜喜").random(), listOf("兴致勃勃", "落落大方", "自言自语")) else createQuestion("下列哪个词语是 ABCC 式的？", listOf("兴致勃勃", "大名鼎鼎", "人才济济").random(), listOf("躲躲藏藏", "干干净净", "人山人海")) }
        9 -> { val q = listOf("“贝”字旁的字通常和 ( ) 有关。" to "钱财", "“皿”字底的字通常和 ( ) 有关。" to "器皿", "“月”字旁的字通常和 ( ) 有关。" to "身体部位").random(); createQuestion(q.first, q.second, listOf("天气", "运动", "植物")) }
        else -> { val q = listOf("要是你在野外迷了路，中午时太阳在 ( ) 边。" to "南", "北极星所在的方向是 ( ) 方。" to "北").random(); createQuestion(q.first, q.second, listOf("东", "西", "南", "北").filter { it != q.second }) }
    }

    // 口算题：三年级中上，难度按 currentDifficulty 分档；乘除以表内为主（口诀还不熟），
    // 难度主要靠加减的凑整范围拉；每道尽量是“有简便方法”的口算题，并带 tip 提示简便法。
    private fun generateGrade2Math(type: Int = -1): Pair<Question, String> {
        val idx = if (type >= 0) type else Random.nextInt(7)
        val diff = currentDifficulty
        val q = when (idx) {
        // 加减：全档两位数口算；进阶/挑战改出“接近整十/整百的凑整题”，得数仍可心算
        0 -> {
            if (diff <= 1) {
                val a = Random.nextInt(10, 90); val b = Random.nextInt(10, 90)
                if (Random.nextBoolean()) createMathQ("$a + $b = ?", a + b) else createMathQ("${maxOf(a, b)} - ${minOf(a, b)} = ?", maxOf(a, b) - minOf(a, b))
            } else {
                val delta = Random.nextInt(1, 4)
                val b = 100 - delta                                    // 97 / 98 / 99，凑整到 100
                if (Random.nextBoolean()) {
                    val a = Random.nextInt(11, 99)                     // 被加数两位数，得数 <200 可口算
                    createMathQ("$a + $b = ?", a + b, "把 $b 看成 100 先加，再减 $delta")
                } else {
                    val a = Random.nextInt(b + 5, 199)                 // 减数接近整百，凑整后口算
                    createMathQ("$a - $b = ?", a - b, "把 $b 看成 100 先减，再加回 $delta")
                }
            }
        }
        // 乘法：始终以表内为主；进阶/挑战偶尔“口诀×整十”（仍靠口诀）
        1 -> {
            val a = Random.nextInt(2, 10)
            if (diff >= 2 && Random.nextInt(100) < 40) {
                val k = Random.nextInt(2, 10); val b = k * 10
                createMathQ("$a × $b = ?", a * b, "先算 $a×$k=${a*k}，末尾添一个 0")
            } else {
                val b = Random.nextInt(2, 10); createMathQ("$a × $b = ?", a * b, "想一想 $a 的乘法口诀")
            }
        }
        // 除法：全档表内有余除法（商 ≤ 9），不出竖式
        2 -> {
            val divisor = Random.nextInt(3, 9); val quotient = Random.nextInt(2, 8); val rem = Random.nextInt(1, divisor)
            createQuestion("${divisor * quotient + rem} ÷ $divisor = ?", "${quotient}余${rem}", listOf("${quotient}余${(rem+1)%divisor}", "${quotient+1}余${rem}", "${quotient-1}余${rem}"), "想 $divisor 乘几最接近")
        }
        // 概念题升级①：克/千克、米/分米/厘米 单位换算计算
        3 -> {
            val n = Random.nextInt(2, 9)
            val items = listOf(
                Triple("$n 千克 = ( ) 克", "${n * 1000}", listOf("${n * 100}", "$n", "${n * 10000}")),
                Triple("${n * 1000} 克 = ( ) 千克", "$n", listOf("${n * 10}", "${n * 100}", "${n * 1000}")),
                Triple("$n 米 = ( ) 厘米", "${n * 100}", listOf("${n * 10}", "$n", "${n * 1000}")),
                Triple("$n 米 = ( ) 分米", "${n * 10}", listOf("${n * 100}", "$n", "${n * 5}")),
                Triple("${n * 10} 分米 = ( ) 米", "$n", listOf("${n * 10}", "${n * 100}", "${n + 10}"))
            )
            val it = items.random(); createQuestion(it.first, it.second, it.third, "大单位换小单位乘进率，小单位换大单位除进率（1千克=1000克，1米=100厘米）")
        }
        // 概念题升级②：平移格数（同向相加 / 反向相消），保留概念又带口算
        4 -> {
            val a = Random.nextInt(2, 7); val b = Random.nextInt(2, 7)
            if (Random.nextBoolean()) createMathQ("一个图形先向右平移 $a 格，又向右平移 $b 格，一共平移了几格？", a + b, "同方向就把两次格数加起来")
            else { val big = maxOf(a, b); val small = minOf(a, b); createMathQ("一个图形先向右平移 $big 格，又向左平移 $small 格，现在离出发点几格？", big - small, "方向相反就相减") }
        }
        // 概念题升级③：万以内数的组成 / 比大小 / 近似数
        5 -> {
            when (Random.nextInt(3)) {
                0 -> { val a = Random.nextInt(1, 10); val b = Random.nextInt(0, 10); val c = Random.nextInt(0, 10); val d = Random.nextInt(0, 10); val num = a*1000 + b*100 + c*10 + d; createQuestion("由 $a 个千、$b 个百、$c 个十、$d 个一组成的数是 ( )", "$num", listOf("${num + 100}", "${num - 10}", "${a*1000 + c*100 + b*10 + d}")) }
                1 -> { val base = Random.nextInt(11, 90); val num = base * 100 + Random.nextInt(1, 100); val rounded = ((num + 50) / 100) * 100; createQuestion("$num ≈ ( )（精确到百位）", "$rounded", listOf("${rounded - 100}", "${rounded + 100}", "${(num/100)*100}")) }
                else -> { val nums = (1..4).map { Random.nextInt(1000, 9999) }.distinct(); val mx = nums.maxOrNull()!!; createQuestion("下面哪个数最大？", "$mx", nums.filter { it != mx }.map { "$it" }) }
            }
        }
        // 混合：表内乘 + 两位数加，得数可心算
        else -> {
            val a = Random.nextInt(2, 10); val b = Random.nextInt(2, 10)
            val c = Random.nextInt(2, 30); createMathQ("$a × $b + $c = ?", a * b + c, "先算乘法 $a×$b，再加 $c")
        }
        }
        val typeName = if (idx < 7) "old-" + grade2TypeNames[idx] else "old-" + grade2TypeNames[6]
        return Pair(q, typeName)
    }

    // 手输得数题：无选项须精确得数，因此全部限两位数加减 + 表内乘，保证口算可及
    private fun generateInputMath(): Question {
        return when (Random.nextInt(3)) {
            0 -> { val a = Random.nextInt(11, 90); val b = Random.nextInt(11, 90); Question("$a + $b = ?", emptyList(), 0, inputAnswer = "${a + b}", tip = "可以先凑整十再加") }
            1 -> { val a = Random.nextInt(30, 99); val b = Random.nextInt(10, a); Question("$a - $b = ?", emptyList(), 0, inputAnswer = "${a - b}", tip = "不够减时要向前一位借 1") }
            else -> { val a = Random.nextInt(2, 10); val b = Random.nextInt(2, 10); Question("$a × $b = ?", emptyList(), 0, inputAnswer = "${a * b}", tip = "想一想 $a 的乘法口诀") }
        }
    }

    private fun createMathQ(text: String, answer: Int, tip: String? = null): Question {
        val correct = answer.toString()
        val wrongs = linkedSetOf<String>()
        // 干扰项取“常见错误值”，紧贴正确答案，避免出现一眼排除的离谱选项：
        // ±1/±2 = 粗心算错；答案较大时加 ±10 = 进退位错误；中等大小加 ±3
        val offsets = mutableListOf(1, -1, 2, -2)
        if (answer >= 5) { offsets.add(3); offsets.add(-3) }
        if (answer >= 20) { offsets.add(10); offsets.add(-10) }
        for (off in offsets.shuffled()) {
            if (wrongs.size >= 3) break
            val w = answer + off
            if (w >= 0 && w != answer) wrongs.add(w.toString())
        }
        // 兜底：仍不足 3 个时，向上取最接近的不同值
        var d = 1
        while (wrongs.size < 3 && d <= 12) {
            val w = answer + d
            if (w != answer) wrongs.add(w.toString())
            d++
        }
        return createQuestion(text, correct, wrongs.toList(), tip)
    }

    private fun createQuestion(text: String, correct: String, wrongs: List<String>, tip: String? = null): Question {
        val uniqueWrongs = wrongs.filter { it != correct }.distinct()
        var allOptions = (uniqueWrongs.take(3) + correct).distinct()
        // 确保至少有3个选项（Question要求2-4个）
        if (allOptions.size < 3) {
            val correctNum = correct.toIntOrNull()
            if (correctNum != null) {
                // 数字题：用临近数字补足，而不是“其他/以上”这种不自然的选项
                var d = 1
                while (allOptions.size < 3 && d <= 10) {
                    for (v in listOf(correctNum + d, correctNum - d)) {
                        val cand = v.toString()
                        if (allOptions.size < 3 && v >= 0 && cand != correct && !allOptions.contains(cand)) {
                            allOptions = allOptions + cand
                        }
                    }
                    d++
                }
            }
            if (allOptions.size < 3 && !allOptions.contains("其他")) allOptions = allOptions + "其他"
            if (allOptions.size < 3 && !allOptions.contains("以上都不对")) allOptions = allOptions + "以上都不对"
        }
        val shuffled = allOptions.shuffled()
        return Question(text, shuffled, shuffled.indexOf(correct), tip = tip)
    }

    // ... (其他方法保持不变，已确保逻辑调用的是经过shuffledOptions处理或者createQuestion生成的)
    fun recordResult(context: Context, question: Question, isCorrect: Boolean, isMath: Boolean) {
        val count = errorRecords[question.text] ?: 0
        errorRecords[question.text] = if (isCorrect) maxOf(0, count - 1) else count + 1
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 今日计数：答题数 / 答对数 / 今日错题（供每晚一日总结）
        rollDayIfNeeded(prefs)
        val ed = prefs.edit().putInt(KEY_DAILY_ANSWERED, prefs.getInt(KEY_DAILY_ANSWERED, 0) + 1)
        if (isCorrect) {
            ed.putInt(KEY_DAILY_CORRECT, prefs.getInt(KEY_DAILY_CORRECT, 0) + 1)
        } else {
            try {
                val arr = JSONArray(prefs.getString(KEY_DAILY_RECORDS, "[]") ?: "[]")
                arr.put(question.text)
                // ponytail: 只保留最近 20 条错题，够家长看且不撑爆 prefs
                val start = maxOf(0, arr.length() - 20)
                val trimmed = JSONArray()
                for (i in start until arr.length()) trimmed.put(arr.getString(i))
                ed.putString(KEY_DAILY_RECORDS, trimmed.toString())
            } catch (e: Exception) {}
        }
        ed.apply()

        val obj = JSONObject()
        errorRecords.forEach { (k, v) -> if (v > 0) obj.put(k, v) }
        prefs.edit().putString(KEY_ERROR_RECORDS, obj.toString()).apply()
        
        // 如果是数学题且答错，记录题型错误
        if (isMath && !isCorrect) {
            val type = detectMathType(question.text)
            if (type != null) {
                val te = mathTypeErrors[type] ?: 0
                mathTypeErrors[type] = te + 1
                val meObj = JSONObject()
                mathTypeErrors.forEach { (k, v) -> if (v > 0) meObj.put(k, v) }
                prefs.edit().putString(KEY_MATH_TYPE_ERRORS, meObj.toString()).apply()
            }
        }
        // 如果答对，错题记录衰减
        if (!isMath && isCorrect) {
            // 语文题答对后，lastSeenRound已记录，下一轮missed自动增加
        }
    }

    // 根据题目文本检测数学题型（细粒度）
    private fun detectMathType(text: String): String? {
        return when {
            "从左数" in text && "从右数" in text -> "old-adv_queue_left_right"
            "排队" in text && "共有" in text -> "old-adv_queue_total"
            "种树" in text -> "old-adv_tree_planting"
            "锯" in text -> "old-adv_saw_log"
            "楼" in text && "秒" in text -> "old-adv_climb_stairs"
            "圆圈" in text && "花" in text -> "old-adv_circle_flower"
            "岁" in text && ("大多少岁" in text || "几年后" in text && "倍" in text) -> "old-adv_age_diff"
            "年龄和" in text -> "old-adv_age_sum"
            "米" in text && "厘米" in text -> "old-adv_unit_convert"
            "划船" in text || "租" in text && "船" in text -> "old-adv_boat_rent"
            "本子" in text && "最多可以买" in text -> "old-adv_buy_notebook"
            "规律排列" in text && "色" in text -> "old-adv_color_pattern"
            "近似数" in text -> "old-adv_approximate"
            "×" in text && "[" in text -> "old-adv_multiply_compare"
            "倍" in text && "共有" in text -> "old-adv_multiple_sum"
            "球" in text && "倍" in text -> "old-adv_basket_balls"
            "岁" in text && "多少倍" in text -> "old-adv_age_multiple"
            "+" in text && "=" in text && "×" !in text -> "old-grade2_add_sub"
            "×" in text && "÷" !in text && "=" in text -> "old-grade2_multiply"
            "÷" in text && "余" in text -> "old-grade2_divide_rem"
            "千克" in text || "克" in text -> "old-grade2_weight"
            "平移" in text || "旋转" in text -> "old-grade2_motion"
            "百位" in text || "千位" in text -> "old-grade2_digits"
            "×" in text && "+" in text -> "old-grade2_mix"
            // 新增 10 种
            "钟面" in text || "时针" in text || "分针" in text -> "old-adv_clock_reading"
            "开始写" in text && "分钟" in text && "写完" in text -> "old-adv_time_calc"
            "面向" in text && "后面" in text -> "old-adv_directions"
            "元" in text && "角" in text && ("=" in text || "等于" in text) -> "old-adv_money_convert"
            "组成" in text && "两位数" in text -> "old-adv_combination"
            "方框" in text && "+" in text -> "old-adv_digit_puzzle"
            "跳了" in text && "多跳" in text -> "old-adv_statistics"
            "几个面" in text || "正方体" in text || "长方体" in text -> "old-adv_observe_3d"
            "一个数加上" in text && "这个数" in text -> "old-adv_simple_equation"
            "平均分给" in text && "还剩" in text -> "old-adv_remainder_app"
            else -> null
        }
    }
    
    // ===== 每日数据累计 + 一日总结（每晚 20:00 推送，当天没答题则不发）=====
    // 跨天自动归零：任何读写前先调用，发现 DailyDataTimestamp 不是今天就清零今日计数
    private fun rollDayIfNeeded(prefs: SharedPreferences) {
        val today = dayKey()
        if (prefs.getInt(KEY_DAILY_DATA_TIMESTAMP, 0) != today) {
            prefs.edit()
                .putInt(KEY_DAILY_DATA_TIMESTAMP, today)
                .putInt(KEY_DAILY_ANSWERED, 0)
                .putInt(KEY_DAILY_CORRECT, 0)
                .putInt(KEY_DAILY_UNLOCK_COUNT, 0)
                .putInt(KEY_DAILY_UNLOCK_MINUTES, 0)
                .putInt(KEY_DAILY_BONUS, 0)
                .putString(KEY_DAILY_RECORDS, "[]")   // 今日错题清单
                .apply()
        }
    }

    fun recordUnlockEvent(context: Context, minutes: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        rollDayIfNeeded(prefs)
        prefs.edit()
            .putInt(KEY_DAILY_UNLOCK_COUNT, prefs.getInt(KEY_DAILY_UNLOCK_COUNT, 0) + 1)
            .putInt(KEY_DAILY_UNLOCK_MINUTES, prefs.getInt(KEY_DAILY_UNLOCK_MINUTES, 0) + minutes)
            .apply()
    }

    // 拼一日总结（详细版）；今天答题数为 0 返回 null（定时跳过 / 手动弹「暂无记录」）
    private fun buildDailyReport(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        rollDayIfNeeded(prefs)
        val answered = prefs.getInt(KEY_DAILY_ANSWERED, 0)
        if (answered == 0) return null
        val correct = prefs.getInt(KEY_DAILY_CORRECT, 0)
        val unlocks = prefs.getInt(KEY_DAILY_UNLOCK_COUNT, 0)
        val minutes = prefs.getInt(KEY_DAILY_UNLOCK_MINUTES, 0)
        val streak = prefs.getInt(KEY_STREAK, 0)
        val stars = prefs.getInt(KEY_STARS, 0)
        val rate = Math.round(correct * 100f / answered)

        val sb = StringBuilder("#### 小欣 · 一日学习总结\n\n")
        sb.append("- **今日解锁:** $unlocks 次，累计奖励 $minutes 分钟\n")
        sb.append("- **今日答题:** 答对 $correct / $answered（正确率 $rate%）\n")
        sb.append("- **坚持:** 🔥 连续 $streak 天 ・ ⭐ 累计 $stars 颗星\n")
        getWeakPointsText(context)?.let { sb.append("- **近期易错（建议辅导）:** $it\n") }
        val wrongs = mutableListOf<String>()
        try {
            val arr = JSONArray(prefs.getString(KEY_DAILY_RECORDS, "[]") ?: "[]")
            for (i in 0 until arr.length()) wrongs.add(arr.getString(i))
        } catch (e: Exception) {}
        if (wrongs.isNotEmpty()) sb.append("\n**今日错题本:**\n- " + wrongs.joinToString("\n- "))
        return sb.toString()
    }

    fun getDailyReport(context: Context): String? = buildDailyReport(context)

    // 发送成功后清零今日计数，避免同一天重复发送
    fun clearDailyReport(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_DAILY_DATA_TIMESTAMP, dayKey())
            .putInt(KEY_DAILY_ANSWERED, 0)
            .putInt(KEY_DAILY_CORRECT, 0)
            .putInt(KEY_DAILY_UNLOCK_COUNT, 0)
            .putInt(KEY_DAILY_UNLOCK_MINUTES, 0)
            .putString(KEY_DAILY_RECORDS, "[]")
            .apply()
    }

    fun hasDailyData(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        rollDayIfNeeded(prefs)
        return prefs.getInt(KEY_DAILY_ANSWERED, 0) > 0
    }

    // 每日解锁次数上限（默认 3）+ 今日已解锁次数（靠答题/超管通关计数；远程家长解锁不计）
    fun getDailyUnlockLimit(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_DAILY_UNLOCK_LIMIT, 3)

    fun setDailyUnlockLimit(context: Context, n: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_DAILY_UNLOCK_LIMIT, n.coerceIn(0, 20)).apply()
    }

    fun getTodayUnlockCount(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        rollDayIfNeeded(prefs)
        return prefs.getInt(KEY_DAILY_UNLOCK_COUNT, 0)
    }

    // 今日临时奖励次数（远程“加一次”），按天清零；有效上限 = limit + bonus
    fun getTodayBonus(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        rollDayIfNeeded(prefs)
        return prefs.getInt(KEY_DAILY_BONUS, 0)
    }

    fun addTodayBonus(context: Context, n: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        rollDayIfNeeded(prefs)
        prefs.edit().putInt(KEY_DAILY_BONUS, prefs.getInt(KEY_DAILY_BONUS, 0) + n.coerceIn(1, 10)).apply()
    }

    // 远程“重置次数/清零”：把今日已用次数与临时奖励都清 0
    fun resetTodayUnlocks(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        rollDayIfNeeded(prefs)
        prefs.edit().putInt(KEY_DAILY_UNLOCK_COUNT, 0).putInt(KEY_DAILY_BONUS, 0).apply()
    }

    // App 更新/重装后清零今日次数（装好从 0 开始，避免一启动就“用完”）
    fun maybeResetOnUpgrade(context: Context, versionCode: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_LAST_VERSION, -1) != versionCode) {
            prefs.edit().putInt(KEY_LAST_VERSION, versionCode)
                .putInt(KEY_DAILY_UNLOCK_COUNT, 0).putInt(KEY_DAILY_BONUS, 0).apply()
        }
    }

    // 解锁次数用完页面用：随机来一道“教学题”（直接亮答案+讲解），记忆类(语文)或技巧类(巧算/思维)
    fun getTeachingCard(): String {
        fun card(label: String, q: Question): String {
            val ans = q.inputAnswer ?: q.options.getOrNull(q.correctIndex).orEmpty()
            val tip = if (!q.tip.isNullOrBlank()) "\n💡 ${q.tip}" else ""
            return "$label\n${q.text}\n✅ 答案：$ans$tip"
        }
        return when (Random.nextInt(4)) {
            0 -> card("📖 语文积累", generateAcademicChineseQuestion())
            1 -> card("💡 巧算技巧", SmartCalcGenerator.generate())
            2 -> card("📜 三上必背", Grade3Recitation.generate())
            else -> card("🧠 思维技巧", ThinkingMathGenerator.generate())
        }
    }
    fun isMathQuestion(text: String): Boolean = true
    fun getTotalQuestionConfig(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_TOTAL_QUESTIONS, 20)
    }

    fun setTotalQuestionConfig(context: Context, count: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_TOTAL_QUESTIONS, count).apply()
    }
    fun getLastReportTime(context: Context): Long =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getLong(KEY_LAST_REPORT_TIME, 0L)

    fun setLastReportTime(context: Context, time: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putLong(KEY_LAST_REPORT_TIME, time).apply()
    }
}
