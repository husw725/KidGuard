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
        restoreOrStartQuiz()
        ReportScheduler.scheduleDailyReport(this)
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

        btnAns1.setOnClickListener { checkAnswer(0) }
        btnAns2.setOnClickListener { checkAnswer(1) }
        btnAns3.setOnClickListener { checkAnswer(2) }
        btnAns4.setOnClickListener { checkAnswer(3) }
        btnRetry.setOnClickListener { startQuiz() }

        tvQuestion.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastQuestionClickTime > 1000) questionClickCount = 0
            questionClickCount++
            lastQuestionClickTime = currentTime
            if (questionClickCount >= 5) {
                questionClickCount = 0
                handleSuperAdminUnlock()
            }
        }
    }

    private fun handleSuperAdminUnlock() {
        val minutes = 3
        thread {
            reportToDingTalkRaw("⚠️ **超管通道已激活**\n- 触发快速解锁\n- 奖励时长: $minutes 分钟")
        }
        getSharedPreferences(PREFS_STATE, Context.MODE_PRIVATE).edit().clear().apply()
        QuestionBank.recordUnlockEvent(this, minutes)
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
        var count = maxOf(5, QuestionBank.getTotalQuestionConfig(this))
        if (QuestionBank.isFirstQuizToday(this)) {
            count = maxOf(5, count / 2)
        }
        currentQuestions = QuestionBank.getRandomQuestions(this, count)
        currentIndex = 0; correctCount = 0; wrongQuestionsList.clear()
        saveState(); showLockScreen(); showCurrentQuestion()
    }

    private fun showCurrentQuestion() {
        if (currentIndex >= currentQuestions.size) { finishQuiz(); return }
        val q = currentQuestions[currentIndex]
        tvProgressText.text = "正在闯关：第 ${currentIndex + 1}/${currentQuestions.size} 题"
        quizProgressBar.max = currentQuestions.size
        quizProgressBar.progress = currentIndex
        tvQuestion.text = q.text
        
        val buttons = listOf(btnAns1, btnAns2, btnAns3, btnAns4)
        buttons.forEachIndexed { index, button ->
            val option = q.options.getOrNull(index)
            if (option != null) {
                button.text = option
                button.visibility = View.VISIBLE
            } else {
                button.visibility = View.GONE
            }
        }
        
        setButtonsEnabled(true); tvFeedback.visibility = View.INVISIBLE
    }

    private fun setButtonsEnabled(e: Boolean) {
        btnAns1.isEnabled = e; btnAns2.isEnabled = e; btnAns3.isEnabled = e; btnAns4.isEnabled = e
    }

    private fun checkAnswer(idx: Int) {
        if (currentIndex >= currentQuestions.size) return
        val q = currentQuestions[currentIndex]; setButtonsEnabled(false)
        val isCorrect = (idx == q.correctIndex)
        val isMath = QuestionBank.isMathQuestion(q.text)
        if (isCorrect) correctCount++ else wrongQuestionsList.add("${q.text} (正确答案: ${q.options[q.correctIndex]})")
        QuestionBank.recordResult(this, q, isCorrect, isMath)
        QuestionBank.updateDifficulty(this, isCorrect)   // 答题表现驱动难度自适应
        tvFeedback.visibility = View.VISIBLE
        if (isCorrect) {
            tvFeedback.text = praiseMessages.random(); tvFeedback.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
            handler.postDelayed({ currentIndex++; saveState(); showCurrentQuestion() }, 600)
        } else {
            val label = ('A' + q.correctIndex).toString()
            tvFeedback.text = "哎呀，答错啦！\n正确答案是: $label. ${q.options[q.correctIndex]}"
            tvFeedback.setTextColor(android.graphics.Color.parseColor("#FF5252"))
            handler.postDelayed({ currentIndex++; saveState(); showCurrentQuestion() }, 3000)
        }
    }

    private fun finishQuiz() {
        getSharedPreferences(PREFS_STATE, Context.MODE_PRIVATE).edit().clear().apply()
        lockContainer.visibility = View.GONE; timerContainer.visibility = View.GONE; resultContainer.visibility = View.VISIBLE
        updateParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, 0, 0)
        
        val totalQuestions = currentQuestions.size
        val score = if(totalQuestions > 0) Math.round(correctCount * (100f / totalQuestions)) else 0
        val required = ceil(totalQuestions * 0.6).toInt(); val passed = correctCount >= required
        
        var minutes = 0
        if (passed) {
            val maxMinutes = 40; val minMinutes = 27
            val extraQuestions = totalQuestions - required
            minutes = if (extraQuestions > 0) {
                minMinutes + (correctCount - required) * (maxMinutes - minMinutes) / extraQuestions
            } else { if (correctCount == totalQuestions) maxMinutes else minMinutes }
            
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            if (hour >= 22 || hour < 7) {
                minutes = maxOf(1, minutes / 10)
            }

            QuestionBank.recordUnlockEvent(this, minutes)
            QuestionBank.markFirstQuizDoneToday(this)

            tvResultTitle.text = "挑战成功！🌟"; tvResultTitle.setTextColor(android.graphics.Color.WHITE)
            tvResultDesc.text = "得分: $score\n奖励解锁时间: $minutes 分钟"
            btnRetry.visibility = View.GONE; handler.postDelayed({ unlockScreen(minutes * 60 * 1000L) }, 2500)
        } else {
            tvResultTitle.text = "再接再厉哦！"; tvResultTitle.setTextColor(android.graphics.Color.WHITE)
            tvResultDesc.text = "得分: $score\n至少需要答对 $required 题 (60分)"
            btnRetry.visibility = View.VISIBLE
        }
        reportToDingTalk(score, passed, correctCount, totalQuestions, minutes, wrongQuestionsList)
    }

    private fun reportToDingTalk(score: Int, passed: Boolean, correct: Int, total: Int, minutes: Int, wrongs: List<String>) {
        val timeText = if(passed) "\n- **奖励时长:** $minutes 分钟" else ""
        val wrongsText = if (wrongs.isNotEmpty()) "\n\n**错题本:**\n- " + wrongs.joinToString("\n- ") else ""
        val content = "#### 小欣学习打卡\n\n- **得分:** $score\n- **正确率:** $correct / $total\n- **结果:** ${if(passed) "✅ 通过" else "❌ 未通过"}$timeText$wrongsText"
        thread {
            reportToDingTalkRaw(content)
        }
    }

    private fun showLockScreen() {
        countDownTimer?.cancel(); lockContainer.visibility = View.VISIBLE; resultContainer.visibility = View.GONE; timerContainer.visibility = View.GONE
        setImmersive(true)
        
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

    override fun onTaskRemoved(ri: Intent?) { super.onTaskRemoved(ri); scheduleRestart() }
    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel(); handler.removeCallbacksAndMessages(null)
        if (::floatingView.isInitialized) try { windowManager.removeView(floatingView) } catch (e: Exception) {}
        scheduleRestart()
    }

    private fun scheduleRestart() {
        val pi = android.app.PendingIntent.getService(applicationContext, 1, Intent(applicationContext, FloatingService::class.java), if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) android.app.PendingIntent.FLAG_ONE_SHOT or android.app.PendingIntent.FLAG_IMMUTABLE else android.app.PendingIntent.FLAG_ONE_SHOT)
        (getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager).set(android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP, android.os.SystemClock.elapsedRealtime() + 1000, pi)
    }
}