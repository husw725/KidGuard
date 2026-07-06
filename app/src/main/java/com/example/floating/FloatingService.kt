package com.example.floating

import android.animation.ValueAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.CountDownTimer
import android.os.Handler
import android.media.MediaPlayer
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.app.NotificationCompat
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.ceil
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import kotlin.concurrent.thread

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var params: WindowManager.LayoutParams
    private var countDownTimer: CountDownTimer? = null

    private lateinit var lockContainer: View
    private lateinit var resultContainer: View
    private lateinit var timerContainer: View
    private lateinit var tvProgressText: TextView
    private lateinit var quizProgressBar: ProgressBar
    private lateinit var tvQuestion: TextView
    private lateinit var tvFeedback: TextView
    private lateinit var btnAns1: Button
    private lateinit var btnAns2: Button
    private lateinit var btnAns3: Button
    private lateinit var btnAns4: Button
    private lateinit var tvResultTitle: TextView
    private lateinit var tvResultDesc: TextView
    private lateinit var btnRetry: Button
    private lateinit var tvTimer: TextView
    private lateinit var btnReplayAudio: Button
    private lateinit var btnHelp: Button
    private lateinit var inputPad: View
    private lateinit var tvInputDisplay: TextView

    // 英语“听音选图”用的预置音频播放（不依赖手机语音引擎）
    private var audioPlayer: MediaPlayer? = null
    private var currentAudioWord: String? = null

    // 手输得数题（③）
    private var inputBuffer = ""
    private var locked = false   // 答题后到下一题之间锁住输入，防重复作答

    // 飞书远程：家长消息横幅 + 快捷回复 + 锁屏期间轮询
    private lateinit var tvParentMsg: TextView
    private lateinit var quickReplyRow: View
    private val feishuPoll = object : Runnable {
        override fun run() {
            thread {
                val cmds = FeishuClient.pollNewMessages(this@FloatingService)
                handler.post {
                    cmds.forEach { applyRemoteCommand(it) }
                    // 自愈：无论哪个入口加了次数，卡在用完屏且已不受限就开题
                    if (onLimitScreen && !dailyLimitReached()) startQuiz()
                }
            }
            handler.postDelayed(this, 45_000)
        }
    }

    private var currentQuestions: List<Question> = emptyList()
    private var currentIndex = 0
    private var correctCount = 0
    private var wrongQuestionsList = mutableListOf<String>()
    private val handler = Handler(Looper.getMainLooper())
    
    private var screenWidth = 0
    private var audioFocusRequest: AudioFocusRequest? = null
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { }

    private var questionClickCount = 0
    private var lastQuestionClickTime = 0L

    // 答对时的随机鼓励语，让小欣每次都有新惊喜
    private val praiseMessages = listOf(
        "真棒！回答正确 ✨", "太厉害啦！全对 🎉", "小欣真聪明 👏", "答对啦，继续加油 💪",
        "完全正确，了不起 🌟", "哇，你真棒 🦄", "答得漂亮 🍭", "聪明的小脑袋瓜 🧠✨",
        "正确！你是小学霸 📚", "棒极了，再接再厉 🚀"
    )

    // 通关时的随机标题（② 正向激励）
    private val passTitles = listOf(
        "挑战成功！🌟", "今天也很棒！🎉", "闯关成功 🏆", "小欣真厉害！💯", "又赢啦！🦄"
    )

    companion object {
        const val PREFS_STATE = "QuizState"
        const val KEY_IN_PROGRESS = "inProgress"
        const val KEY_INDEX = "index"
        const val KEY_CORRECT = "correct"
        const val KEY_QUESTIONS = "questions"
        const val KEY_WRONGS = "wrongs"
        const val WEBHOOK_URL = "https://oapi.dingtalk.com/robot/send?access_token=e63f4fc767085735a440e559b77b4a599f41868f7928b9b9bedc6e65d4654de3"
        
        fun reportToDingTalkRaw(markdownContent: String): Boolean {
            return try {
                val conn = URL(WEBHOOK_URL).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"; conn.setRequestProperty("Content-Type", "application/json; charset=utf-8"); conn.doOutput = true
                val out = OutputStreamWriter(conn.outputStream, "UTF-8")
                out.write(JSONObject().put("msgtype", "markdown").put("markdown", JSONObject().put("title", "小欣学习通知").put("text", markdownContent)).toString())
                out.flush(); out.close()
                conn.responseCode in 200..299
            } catch (e: Exception) {
                false
            }
        }

        fun sendDailyReport(context: Context) {
            thread {
                val reportContent = QuestionBank.getDailyReport(context)
                if (!reportContent.isNullOrEmpty()) {
                    val success = reportToDingTalkRaw(reportContent)
                    if (success) {
                        QuestionBank.clearDailyReport(context)
                        QuestionBank.setLastReportTime(context, System.currentTimeMillis())
                    }
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "floating_service_channel")
            .setContentTitle("小欣学习服务正在运行")
            .setSmallIcon(R.drawable.ic_cute_launcher)
            .setOngoing(true).build()
        startForeground(1, notification)
        // 后台加了答题次数后唤醒：若正卡在用完屏且已不再受限，立即开题
        if (intent?.getBooleanExtra("reeval_limit", false) == true && onLimitScreen && !dailyLimitReached()) {
            startQuiz()
        }
        // 后台收到“解锁/暂停”后唤醒：正锁着就立即放行到暂停结束
        if (intent?.getBooleanExtra("reeval_pause", false) == true) {
            val pauseLeft = getPauseUntil() - System.currentTimeMillis()
            if (pauseLeft > 0 && lockContainer.visibility == View.VISIBLE) {
                getSharedPreferences(PREFS_STATE, Context.MODE_PRIVATE).edit().clear().apply()
                unlockScreen(pauseLeft)
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("floating_service_channel", "锁屏服务", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        screenWidth = metrics.widthPixels

        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_widget, null)
        initViews()
        setupDrag()

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        params = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            type = layoutFlag
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.TOP or Gravity.START
        }

        windowManager.addView(floatingView, params)
        QuestionBank.maybeResetOnUpgrade(this, BuildConfig.VERSION_CODE)  // 更新/重装后今日次数清零
        restoreOrStartQuiz()
        ReportScheduler.scheduleDailyReport(this)
        ReportScheduler.scheduleFeishuPoll(this)   // 未锁屏时也轮询飞书指令
        val ctx = applicationContext
        thread { FeishuClient.sendHelpOnce(ctx) }   // 首次连通发一条指令帮助到群
    }

    // 单词 -> res/raw 资源 id（小写、空格转下划线）；找不到返回 0
    private fun audioResId(word: String): Int {
        val name = word.lowercase().replace(" ", "_")
        return resources.getIdentifier(name, "raw", packageName)
    }

    private fun playCurrentWord() {
        val word = currentAudioWord ?: return
        val resId = audioResId(word)
        if (resId == 0) return
        try {
            audioPlayer?.release()
            audioPlayer = MediaPlayer.create(this, resId)?.apply {
                setOnCompletionListener { mp -> mp.release(); if (audioPlayer === mp) audioPlayer = null }
                start()
            }
        } catch (e: Exception) {}
    }
    
    private fun setImmersive(immersive: Boolean) {
        if (immersive) {
            floatingView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN)
        } else {
            floatingView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    private fun initViews() {
        lockContainer = floatingView.findViewById(R.id.lock_container)
        resultContainer = floatingView.findViewById(R.id.result_container)
        timerContainer = floatingView.findViewById(R.id.timer_container)
        tvProgressText = floatingView.findViewById(R.id.tv_progress_text)
        quizProgressBar = floatingView.findViewById(R.id.quiz_progress_bar)
        tvQuestion = floatingView.findViewById(R.id.tv_question)
        tvFeedback = floatingView.findViewById(R.id.tv_feedback)
        btnAns1 = floatingView.findViewById(R.id.btn_ans_1)
        btnAns2 = floatingView.findViewById(R.id.btn_ans_2)
        btnAns3 = floatingView.findViewById(R.id.btn_ans_3)
        btnAns4 = floatingView.findViewById(R.id.btn_ans_4)
        tvResultTitle = floatingView.findViewById(R.id.tv_result_title)
        tvResultDesc = floatingView.findViewById(R.id.tv_result_desc)
        btnRetry = floatingView.findViewById(R.id.btn_retry)
        tvTimer = floatingView.findViewById(R.id.tv_timer)
        btnReplayAudio = floatingView.findViewById(R.id.btn_replay_audio)
        btnHelp = floatingView.findViewById(R.id.btn_help)

        btnAns1.setOnClickListener { checkAnswer(0) }
        btnAns2.setOnClickListener { checkAnswer(1) }
        btnAns3.setOnClickListener { checkAnswer(2) }
        btnAns4.setOnClickListener { checkAnswer(3) }
        btnRetry.setOnClickListener { startQuiz() }
        btnReplayAudio.setOnClickListener { playCurrentWord() }
        btnHelp.setOnClickListener { showHelp() }

        inputPad = floatingView.findViewById(R.id.input_pad)
        tvInputDisplay = floatingView.findViewById(R.id.tv_input_display)
        for (d in 0..9) {
            val bid = resources.getIdentifier("btn_k$d", "id", packageName)
            if (bid != 0) floatingView.findViewById<Button>(bid).setOnClickListener { appendDigit(d.toString()) }
        }
        floatingView.findViewById<Button>(R.id.btn_kdel).setOnClickListener { backspaceInput() }
        floatingView.findViewById<Button>(R.id.btn_kok).setOnClickListener { submitInput() }

        tvParentMsg = floatingView.findViewById(R.id.tv_parent_msg)
        quickReplyRow = floatingView.findViewById(R.id.quick_reply_row)
        listOf(R.id.btn_qr_1 to "好的👌", R.id.btn_qr_2 to "知道啦", R.id.btn_qr_3 to "想你了❤️", R.id.btn_qr_4 to "等下就好")
            .forEach { (id, label) -> floatingView.findViewById<Button>(id).setOnClickListener { sendQuickReply(label) } }

        // 1 秒内连点 5 次触发超管应急解锁；题目文字和结果标题（次数用完屏）都挂同一个入口
        val superAdminTap = View.OnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastQuestionClickTime > 1000) questionClickCount = 0
            questionClickCount++
            lastQuestionClickTime = now
            if (questionClickCount >= 5) { questionClickCount = 0; handleSuperAdminUnlock() }
        }
        tvQuestion.setOnClickListener(superAdminTap)
        tvResultTitle.setOnClickListener(superAdminTap)
    }

    private fun handleSuperAdminUnlock() {
        val minutes = 3
        thread {
            reportToDingTalkRaw("⚠️ **超管通道已激活**\n- 触发快速解锁\n- 奖励时长: $minutes 分钟")
        }
        getSharedPreferences(PREFS_STATE, Context.MODE_PRIVATE).edit().clear().apply()
        // 超管是家长应急通道，不计入每日解锁次数（与远程解锁一致）
        unlockScreen(minutes * 60 * 1000L)
    }

    private fun saveState() {
        val prefs = getSharedPreferences(PREFS_STATE, Context.MODE_PRIVATE)
        val qArray = JSONArray()
        currentQuestions.forEach { qArray.put(it.toJsonObject()) }
        val wArray = JSONArray()
        wrongQuestionsList.forEach { wArray.put(it) }
        prefs.edit().putBoolean(KEY_IN_PROGRESS, true).putInt(KEY_INDEX, currentIndex).putInt(KEY_CORRECT, correctCount).putString(KEY_QUESTIONS, qArray.toString()).putString(KEY_WRONGS, wArray.toString()).apply()
    }

    private fun restoreOrStartQuiz() {
        val pauseLeft = getPauseUntil() - System.currentTimeMillis()
        if (pauseLeft > 0) { unlockScreen(pauseLeft); return }   // 飞书远程暂停期：不锁
        if (dailyLimitReached()) { showLimitReachedScreen(); return }  // 当天解锁次数用完：不出题
        val prefs = getSharedPreferences(PREFS_STATE, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_IN_PROGRESS, false)) {
            currentIndex = prefs.getInt(KEY_INDEX, 0)
            correctCount = prefs.getInt(KEY_CORRECT, 0)
            val qStr = prefs.getString(KEY_QUESTIONS, "[]") ?: "[]"
            val wStr = prefs.getString(KEY_WRONGS, "[]") ?: "[]"
            try {
                val qArray = JSONArray(qStr); val qList = mutableListOf<Question>()
                for (i in 0 until qArray.length()) qList.add(Question.fromJsonObject(qArray.getJSONObject(i)))
                currentQuestions = qList
                val wArray = JSONArray(wStr); wrongQuestionsList.clear()
                for (i in 0 until wArray.length()) wrongQuestionsList.add(wArray.getString(i))
                if (currentQuestions.isNotEmpty()) { showLockScreen(); showCurrentQuestion(); return }
            } catch (e: Exception) {}
        }
        startQuiz()
    }

    private fun startQuiz() {
        val pauseLeft = getPauseUntil() - System.currentTimeMillis()
        if (pauseLeft > 0) { unlockScreen(pauseLeft); return }   // 飞书远程暂停期：不出题
        if (dailyLimitReached()) { showLimitReachedScreen(); return }  // 当天解锁次数用完：不出题
        onLimitScreen = false
        val count = maxOf(20, QuestionBank.getTotalQuestionConfig(this))
        currentQuestions = QuestionBank.getRandomQuestions(this, count)
        currentIndex = 0; correctCount = 0; wrongQuestionsList.clear()
        saveState(); showLockScreen(); showCurrentQuestion()
    }

    private fun showCurrentQuestion() {
        if (currentIndex >= currentQuestions.size) { finishQuiz(); return }
        val q = currentQuestions[currentIndex]
        tvProgressText.visibility = View.VISIBLE; quizProgressBar.visibility = View.VISIBLE  // 从“次数用完屏”切回时恢复
        tvProgressText.text = "正在闯关：第 ${currentIndex + 1}/${currentQuestions.size} 题"
        quizProgressBar.max = currentQuestions.size
        quizProgressBar.progress = currentIndex

        // 听音选图题：播放单词发音、放大 emoji 选项、显示重听按钮
        currentAudioWord = q.audioWord
        val isAudio = q.audioWord != null
        val hasAudio = isAudio && audioResId(q.audioWord!!) != 0
        if (isAudio && !hasAudio) {
            tvQuestion.text = "请选出：${q.audioWord}"   // 兜底：万一音频缺失，显示单词
        } else {
            tvQuestion.text = q.text
        }
        btnReplayAudio.visibility = if (isAudio) View.VISIBLE else View.GONE
        // 有思路可讲（tip 非空）才显示求助按钮；语文认读/英语听音题多数无 tip，自然不显示
        btnHelp.visibility = if (q.tip != null) View.VISIBLE else View.GONE

        // 手输得数题：显示数字键盘（选项为空，4 个选项按钮会自动隐藏）
        val isInput = q.inputAnswer != null
        inputPad.visibility = if (isInput) View.VISIBLE else View.GONE
        if (isInput) { inputBuffer = ""; tvInputDisplay.text = "" }

        val optionTextSize = if (isAudio) 34f else 18f

        val buttons = listOf(btnAns1, btnAns2, btnAns3, btnAns4)
        buttons.forEachIndexed { index, button ->
            val option = q.options.getOrNull(index)
            if (option != null) {
                button.text = option
                button.textSize = optionTextSize
                button.visibility = View.VISIBLE
            } else {
                button.visibility = View.GONE
            }
        }

        setButtonsEnabled(true); locked = false; tvFeedback.visibility = View.INVISIBLE
        if (hasAudio) handler.postDelayed({ playCurrentWord() }, 350)
    }

    private fun setButtonsEnabled(e: Boolean) {
        btnAns1.isEnabled = e; btnAns2.isEnabled = e; btnAns3.isEnabled = e; btnAns4.isEnabled = e
    }

    private fun checkAnswer(idx: Int) {
        if (locked || currentIndex >= currentQuestions.size) return
        val q = currentQuestions[currentIndex]
        handleAnswer(q, idx == q.correctIndex)
    }

    // 求助：只显示解题思路，不给答案、不判对错、不锁定，孩子想清楚后照常作答
    private fun showHelp() {
        if (locked || currentIndex >= currentQuestions.size) return
        val tip = currentQuestions[currentIndex].tip ?: return
        tvFeedback.text = "💡 想一想：$tip"
        tvFeedback.setTextColor(android.graphics.Color.parseColor("#1976D2"))
        tvFeedback.visibility = View.VISIBLE
    }

    private fun appendDigit(s: String) {
        if (locked) return
        if (inputBuffer.length < 5) { inputBuffer += s; tvInputDisplay.text = inputBuffer }
    }

    private fun backspaceInput() {
        if (locked || inputBuffer.isEmpty()) return
        inputBuffer = inputBuffer.dropLast(1); tvInputDisplay.text = inputBuffer
    }

    private fun submitInput() {
        if (locked || inputBuffer.isEmpty() || currentIndex >= currentQuestions.size) return
        val q = currentQuestions[currentIndex]
        handleAnswer(q, inputBuffer == q.inputAnswer)
    }

    private fun handleAnswer(q: Question, isCorrect: Boolean) {
        if (locked) return
        locked = true; setButtonsEnabled(false)
        val isMath = QuestionBank.isMathQuestion(q.text)
        if (isCorrect) correctCount++ else {
            val desc = if (q.audioWord != null) "听音选图「${q.audioWord}」" else q.text
            val ans = q.inputAnswer ?: q.options[q.correctIndex]
            wrongQuestionsList.add("$desc (正确答案: $ans)")
        }
        QuestionBank.recordResult(this, q, isCorrect, isMath)
        QuestionBank.updateDifficulty(this, isCorrect)   // 答题表现驱动难度自适应
        q.masteryKey?.let { QuestionBank.recordRecitationResult(this, it, isCorrect) }  // 必背内容掌握度（答错回教学卡）
        tvFeedback.visibility = View.VISIBLE
        if (q.audioWord != null) {
            // 英语听音选图：答完显示“图 + 单词拼写”并复读一遍（音—形—义再连一次），更新逐词掌握度
            QuestionBank.recordEnglishResult(this, q.audioWord!!, isCorrect)
            val emoji = q.options[q.correctIndex]
            tvFeedback.text = if (isCorrect) "真棒！$emoji ${q.audioWord}" else "正确答案：$emoji ${q.audioWord}"
            tvFeedback.setTextColor(android.graphics.Color.parseColor(if (isCorrect) "#4CAF50" else "#FF5252"))
            handler.postDelayed({ playCurrentWord() }, 300)
            handler.postDelayed({ currentIndex++; saveState(); showCurrentQuestion() }, 1800)
        } else if (isCorrect) {
            tvFeedback.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
            if (q.masteryKey != null && q.tip != null) {
                // 必背新内容：答对也复看一遍解析，强化记忆（其余题保持快节奏）
                tvFeedback.text = "${praiseMessages.random()}\n💡 ${q.tip}"
                handler.postDelayed({ currentIndex++; saveState(); showCurrentQuestion() }, 2600)
            } else {
                tvFeedback.text = praiseMessages.random()
                handler.postDelayed({ currentIndex++; saveState(); showCurrentQuestion() }, 600)
            }
        } else {
            val correctText = if (q.inputAnswer != null) q.inputAnswer
                else "${('A' + q.correctIndex)}. ${q.options[q.correctIndex]}"
            val base = "哎呀，答错啦！\n正确答案是: $correctText"
            tvFeedback.text = if (q.tip != null) "$base\n💡 ${q.tip}" else base
            tvFeedback.setTextColor(android.graphics.Color.parseColor("#FF5252"))
            // 有讲解时多停留，让她读完
            handler.postDelayed({ currentIndex++; saveState(); showCurrentQuestion() }, if (q.tip != null) 4500L else 3000L)
        }
    }

    private fun finishQuiz() {
        getSharedPreferences(PREFS_STATE, Context.MODE_PRIVATE).edit().clear().apply()
        lockContainer.visibility = View.GONE; timerContainer.visibility = View.GONE; resultContainer.visibility = View.VISIBLE
        updateParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, 0, 0)
        
        val totalQuestions = currentQuestions.size
        val score = if(totalQuestions > 0) Math.round(correctCount * (100f / totalQuestions)) else 0
        // 连续两次没通关就临时放宽到 50%，避免彻底卡死、哭鼻子（⑤ 降焦虑）
        val failCount = QuestionBank.getQuizFailCount(this)
        val passRatio = if (failCount >= 2) 0.5 else 0.6
        val required = ceil(totalQuestions * passRatio).toInt(); val passed = correctCount >= required
        QuestionBank.setQuizFailCount(this, if (passed) 0 else failCount + 1)

        var minutes = 0
        if (passed) {
            val maxMinutes = 40; val minMinutes = 27
            val extraQuestions = totalQuestions - required
            minutes = if (extraQuestions > 0) {
                minMinutes + (correctCount - required) * (maxMinutes - minMinutes) / extraQuestions
            } else { if (correctCount == totalQuestions) maxMinutes else minMinutes }
            
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            if (hour >= 22 || hour < 7) {
                minutes = maxOf(5, minutes / 3)   // 夜间时长收窄但不至于太挫败（⑤ 降焦虑）
            }

            QuestionBank.recordUnlockEvent(this, minutes)

            val (streak, stars) = QuestionBank.recordPass(this)
            tvResultTitle.text = passTitles.random(); tvResultTitle.setTextColor(android.graphics.Color.WHITE)
            tvResultDesc.text = "得分: $score　⭐ x$stars\n🔥 连续学习 $streak 天\n奖励解锁: $minutes 分钟"
            btnRetry.visibility = View.GONE; handler.postDelayed({ unlockScreen(minutes * 60 * 1000L) }, 2500)
        } else {
            tvResultTitle.text = "再接再厉哦！"; tvResultTitle.setTextColor(android.graphics.Color.WHITE)
            // 正向化措辞：先肯定答对的，再说差几题（②的轻量版，正式版下一轮做）
            val gap = (required - correctCount).coerceAtLeast(1)
            val eased = if (passRatio < 0.6) "\n（这次门槛已放低，加油就能过）" else ""
            tvResultDesc.text = "已经答对 $correctCount 题啦！再对 $gap 题就能通关 💪$eased"
            btnRetry.visibility = View.VISIBLE
        }
        reportToDingTalk(score, passed, correctCount, totalQuestions, minutes, wrongQuestionsList)
    }

    private fun reportToDingTalk(score: Int, passed: Boolean, correct: Int, total: Int, minutes: Int, wrongs: List<String>) {
        val timeText = if(passed) "\n- **奖励时长:** $minutes 分钟" else ""
        val weak = QuestionBank.getWeakPointsText(this)
        val weakText = if (weak != null) "\n- **近期易错（建议辅导）:** $weak" else ""
        val wrongsText = if (wrongs.isNotEmpty()) "\n\n**错题本:**\n- " + wrongs.joinToString("\n- ") else ""
        val content = "#### 小欣学习打卡\n\n- **得分:** $score\n- **正确率:** $correct / $total\n- **结果:** ${if(passed) "✅ 通过" else "❌ 未通过"}$timeText$weakText$wrongsText"
        thread {
            reportToDingTalkRaw(content)
        }
    }

    private fun showLockScreen() {
        countDownTimer?.cancel(); lockContainer.visibility = View.VISIBLE; resultContainer.visibility = View.GONE; timerContainer.visibility = View.GONE
        setImmersive(true)
        // 锁屏期间勤轮询飞书指令 + 展示未读家长消息
        handler.removeCallbacks(feishuPoll); handler.post(feishuPoll)
        consumePendingParentMsg()

        // Update flags to intercept touches (Remove NOT_FOCUSABLE)
        params.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        updateParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, 0, 0)
        
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()).setOnAudioFocusChangeListener(audioFocusChangeListener).build()
            audioFocusRequest = req; am.requestAudioFocus(req)
        } else { @Suppress("DEPRECATION") am.requestAudioFocus(audioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) }
    }

    private fun unlockScreen(ms: Long) {
        onLimitScreen = false
        handler.removeCallbacks(feishuPoll)   // 解锁/暂停期间停止勤轮询（由 15min 周期闹钟兜底）
        tvParentMsg.visibility = View.GONE; quickReplyRow.visibility = View.GONE
        lockContainer.visibility = View.GONE; resultContainer.visibility = View.GONE; timerContainer.visibility = View.VISIBLE
        setImmersive(false); val size = (60 * resources.displayMetrics.density).toInt()
        
        // Update flags to allow touching background apps (Add NOT_FOCUSABLE)
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        updateParams(size, size, screenWidth - size, 200); startTimer(ms)
        
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { audioFocusRequest?.let { am.abandonAudioFocusRequest(it) } } else { @Suppress("DEPRECATION") am.abandonAudioFocus(audioFocusChangeListener) }
    }

    private fun updateParams(w: Int, h: Int, x: Int, y: Int) {
        params.width = w; params.height = h; params.x = x; params.y = y; try { windowManager.updateViewLayout(floatingView, params) } catch (e: Exception) {}
    }

    private fun startTimer(ms: Long) {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(ms, 1000) {
            override fun onTick(rem: Long) { val s = ceil(rem / 1000.0).toInt(); tvTimer.text = if (s >= 60) (s/60).toString() else s.toString() }
            override fun onFinish() {
                // Reliable Home press via Accessibility Service
                LockAccessibilityService.pressHome()
                
                // Then show the lock screen
                startQuiz() 
            }
        }.start()
    }

    private fun setupDrag() {
        timerContainer.setOnTouchListener(object : View.OnTouchListener {
            private var ix = 0; private var iy = 0; private var itx = 0f; private var ity = 0f; private var moved = false
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { ix = params.x; iy = params.y; itx = e.rawX; ity = e.rawY; moved = false; return true }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - itx).toInt(); val dy = (e.rawY - ity).toInt()
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) moved = true
                        params.x = ix + dx; params.y = iy + dy; try { windowManager.updateViewLayout(floatingView, params) } catch (ex: Exception) {}
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (moved) {
                            val targetX = if (params.x + v.width / 2 < screenWidth / 2) 0 else screenWidth - v.width
                            ValueAnimator.ofInt(params.x, targetX).apply { duration = 200; addUpdateListener { animation -> params.x = animation.animatedValue as Int; try { windowManager.updateViewLayout(floatingView, params) } catch (ex: Exception) {} } }.start()
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    // ===== 飞书远程指令 =====
    private fun applyRemoteCommand(text: String) {
        when (val c = FeishuClient.parseCommand(text)) {
            is FeishuClient.Command.UnlockNow -> {
                thread { FeishuClient.sendText("✅ 已远程解锁 ${c.minutes} 分钟") }
                getSharedPreferences(PREFS_STATE, Context.MODE_PRIVATE).edit().clear().apply()
                unlockScreen(c.minutes * 60 * 1000L)
            }
            is FeishuClient.Command.LockNow -> {
                setPauseUntil(0L)
                if (lockContainer.visibility != View.VISIBLE) startQuiz()
            }
            is FeishuClient.Command.PauseLock -> {
                setPauseUntil(System.currentTimeMillis() + c.minutes * 60 * 1000L)
                thread { FeishuClient.sendText("✅ 已暂停锁屏 ${c.minutes} 分钟") }
                unlockScreen(c.minutes * 60 * 1000L)
            }
            is FeishuClient.Command.SetUnlockLimit -> {
                QuestionBank.setDailyUnlockLimit(this, c.limit)
                thread { FeishuClient.sendText("✅ 每日可答题解锁次数已设为 ${c.limit} 次（今日已解锁 ${QuestionBank.getTodayUnlockCount(this)} 次）") }
            }
            is FeishuClient.Command.GrantExtra -> {
                QuestionBank.addTodayBonus(this, c.times)
                thread { FeishuClient.sendText("✅ 今天临时增加 ${c.times} 次答题机会") }
                if (onLimitScreen && !dailyLimitReached()) startQuiz()   // 她正卡在用完屏：马上给题
            }
            is FeishuClient.Command.ResetUnlocks -> {
                QuestionBank.resetTodayUnlocks(this)
                thread { FeishuClient.sendText("✅ 今日解锁次数已重置") }
                if (onLimitScreen && !dailyLimitReached()) startQuiz()
            }
            is FeishuClient.Command.Help -> thread { FeishuClient.sendText(FeishuClient.HELP_TEXT) }
            is FeishuClient.Command.MessageToChild -> showParentMessage(c.text)
        }
    }

    private var onLimitScreen = false

    private fun dailyLimitReached(): Boolean =
        QuestionBank.getTodayUnlockCount(this) >= QuestionBank.getDailyUnlockLimit(this) + QuestionBank.getTodayBonus(this)

    // 当天答题解锁次数用完：在 lock_container 内渲染（这样家长消息/快捷回复可见），不出题，仍轮询飞书让家长救场
    private fun showLimitReachedScreen() {
        onLimitScreen = true
        showLockScreen()                       // 铺满 + 启动飞书轮询 + consumePendingParentMsg（此时可真正显示）
        resultContainer.visibility = View.GONE
        // 隐藏答题相关视图，只留标题/消息/快捷回复
        tvProgressText.visibility = View.GONE
        quizProgressBar.visibility = View.GONE
        btnReplayAudio.visibility = View.GONE
        inputPad.visibility = View.GONE
        listOf(btnAns1, btnAns2, btnAns3, btnAns4).forEach { it.visibility = View.GONE }
        tvFeedback.visibility = View.INVISIBLE
        tvQuestion.text = "今天的解锁次数用完啦 🔒\n已解锁 ${QuestionBank.getDailyUnlockLimit(this)} 次，明天再来吧！想继续请爸爸妈妈远程解锁。\n\n顺便学一个 👇\n${QuestionBank.getTeachingCard()}"
    }

    private fun showParentMessage(text: String) {
        tvParentMsg.text = "爸爸说：$text"
        tvParentMsg.visibility = View.VISIBLE
        quickReplyRow.visibility = View.VISIBLE
    }

    private fun sendQuickReply(label: String) {
        thread { FeishuClient.sendText("小欣：$label") }
        tvParentMsg.visibility = View.GONE; quickReplyRow.visibility = View.GONE
    }

    private fun consumePendingParentMsg() {
        val p = getSharedPreferences(FeishuClient.PREFS, Context.MODE_PRIVATE)
        val msg = p.getString(FeishuClient.KEY_PENDING_MSG, null)
        if (!msg.isNullOrBlank()) {
            showParentMessage(msg)
            p.edit().remove(FeishuClient.KEY_PENDING_MSG).apply()
        }
    }

    private fun getPauseUntil(): Long =
        getSharedPreferences(FeishuClient.PREFS, Context.MODE_PRIVATE).getLong(FeishuClient.KEY_PAUSE_UNTIL, 0L)

    private fun setPauseUntil(t: Long) =
        getSharedPreferences(FeishuClient.PREFS, Context.MODE_PRIVATE).edit().putLong(FeishuClient.KEY_PAUSE_UNTIL, t).apply()

    override fun onTaskRemoved(ri: Intent?) { super.onTaskRemoved(ri); scheduleRestart() }
    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel(); handler.removeCallbacksAndMessages(null)
        try { audioPlayer?.release() } catch (e: Exception) {}
        audioPlayer = null
        if (::floatingView.isInitialized) try { windowManager.removeView(floatingView) } catch (e: Exception) {}
        scheduleRestart()
    }

    private fun scheduleRestart() {
        val pi = android.app.PendingIntent.getService(applicationContext, 1, Intent(applicationContext, FloatingService::class.java), if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) android.app.PendingIntent.FLAG_ONE_SHOT or android.app.PendingIntent.FLAG_IMMUTABLE else android.app.PendingIntent.FLAG_ONE_SHOT)
        val am = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val at = android.os.SystemClock.elapsedRealtime() + 1000
        // Doze/省电下普通 set() 会被批量推迟到维护窗口，用 AllowWhileIdle 保证及时重启
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
        } else {
            am.setExact(android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
        }
    }
}