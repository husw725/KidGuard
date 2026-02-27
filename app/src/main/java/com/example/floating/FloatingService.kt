package com.example.floating

import android.animation.ValueAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
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
import android.media.AudioManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest

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

    private companion object {
        const val PREFS_STATE = "QuizState"
        const val KEY_IN_PROGRESS = "inProgress"
        const val KEY_INDEX = "index"
        const val KEY_CORRECT = "correct"
        const val KEY_QUESTIONS = "questions"
        const val KEY_WRONGS = "wrongs"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "floating_service_channel")
            .setContentTitle("锁屏服务运行中")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
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
        QuestionBank.initAndSyncCloud(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        screenWidth = metrics.widthPixels

        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_widget, null)
        initViews()
        setupDrag()

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            layoutFlag, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        windowManager.addView(floatingView, params)
        
        restoreOrStartQuiz()
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
    }

    private fun saveState() {
        val prefs = getSharedPreferences(PREFS_STATE, Context.MODE_PRIVATE)
        val qArray = JSONArray()
        currentQuestions.forEach { qArray.put(it.toJsonObject()) }
        val wArray = JSONArray()
        wrongQuestionsList.forEach { wArray.put(it) }
        
        prefs.edit()
            .putBoolean(KEY_IN_PROGRESS, true)
            .putInt(KEY_INDEX, currentIndex)
            .putInt(KEY_CORRECT, correctCount)
            .putString(KEY_QUESTIONS, qArray.toString())
            .putString(KEY_WRONGS, wArray.toString())
            .apply()
    }

    private fun clearState() {
        getSharedPreferences(PREFS_STATE, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun restoreOrStartQuiz() {
        val prefs = getSharedPreferences(PREFS_STATE, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_IN_PROGRESS, false)) {
            currentIndex = prefs.getInt(KEY_INDEX, 0)
            correctCount = prefs.getInt(KEY_CORRECT, 0)
            
            val qStr = prefs.getString(KEY_QUESTIONS, "[]") ?: "[]"
            val qList = mutableListOf<Question>()
            val qArray = JSONArray(qStr)
            for (i in 0 until qArray.length()) qList.add(Question.fromJsonObject(qArray.getJSONObject(i)))
            currentQuestions = qList
            
            wrongQuestionsList.clear()
            val wStr = prefs.getString(KEY_WRONGS, "[]") ?: "[]"
            val wArray = JSONArray(wStr)
            for (i in 0 until wArray.length()) wrongQuestionsList.add(wArray.getString(i))
            
            if (currentQuestions.isNotEmpty()) {
                showLockScreen()
                showCurrentQuestion()
                return
            }
        }
        startQuiz()
    }

    private fun startQuiz() {
        val count = maxOf(10, QuestionBank.getTotalQuestionConfig(this))
        currentQuestions = QuestionBank.getRandomQuestions(this, count)
        currentIndex = 0
        correctCount = 0
        wrongQuestionsList.clear()
        saveState()
        showLockScreen()
        showCurrentQuestion()
    }

    private fun showCurrentQuestion() {
        if (currentIndex >= currentQuestions.size) {
            finishQuiz()
            return
        }
        val q = currentQuestions[currentIndex]
        tvProgressText.text = "正在闯关：第 ${currentIndex + 1}/${currentQuestions.size} 题"
        quizProgressBar.max = currentQuestions.size
        quizProgressBar.progress = currentIndex
        tvQuestion.text = q.text
        btnAns1.text = q.options.getOrNull(0) ?: ""
        btnAns2.text = q.options.getOrNull(1) ?: ""
        btnAns3.text = q.options.getOrNull(2) ?: ""
        btnAns4.text = q.options.getOrNull(3) ?: ""
        setButtonsEnabled(true)
        tvFeedback.visibility = View.INVISIBLE
    }

    private fun setButtonsEnabled(e: Boolean) {
        btnAns1.isEnabled = e; btnAns2.isEnabled = e; btnAns3.isEnabled = e; btnAns4.isEnabled = e
    }

    private fun checkAnswer(idx: Int) {
        val q = currentQuestions[currentIndex]
        setButtonsEnabled(false)
        val isCorrect = (idx == q.correctIndex)
        if (isCorrect) {
            correctCount++
        } else {
            val correctOption = q.options[q.correctIndex]
            wrongQuestionsList.add("${q.text} (正确答案: $correctOption)")
        }
        QuestionBank.recordResult(this, q, isCorrect)

        tvFeedback.visibility = View.VISIBLE
        if (isCorrect) {
            tvFeedback.text = "真棒！回答正确 ✨"
            tvFeedback.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
            handler.postDelayed({ nextQuestion() }, 600)
        } else {
            val correctLabel = ('A' + q.correctIndex).toString()
            tvFeedback.text = "哎呀，答错啦！\n正确答案是: $correctLabel. ${q.options[q.correctIndex]}"
            tvFeedback.setTextColor(android.graphics.Color.parseColor("#FF5252"))
            handler.postDelayed({ nextQuestion() }, 3000)
        }
    }

    private fun nextQuestion() {
        currentIndex++
        saveState()
        showCurrentQuestion()
    }

    private fun finishQuiz() {
        clearState()
        lockContainer.visibility = View.GONE; timerContainer.visibility = View.GONE; resultContainer.visibility = View.VISIBLE
        updateParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, 0, 0)

        // 避免分数超100或有小数，直接使用比例
        val score = Math.round(correctCount * (100f / currentQuestions.size))
        val required = ceil(currentQuestions.size * 0.6).toInt()
        val isPassed = correctCount >= required
        
        reportToDingTalk(score, isPassed, correctCount, currentQuestions.size, wrongQuestionsList)

        if (isPassed) {
            val maxMinutes = 60
            val minMinutes = 40
            val extraQuestions = currentQuestions.size - required
            val minutes = if (extraQuestions > 0) {
                minMinutes + (correctCount - required) * (maxMinutes - minMinutes) / extraQuestions
            } else {
                if (correctCount == currentQuestions.size) maxMinutes else minMinutes
            }
            
            tvResultTitle.text = "挑战成功！🌟"; tvResultTitle.setTextColor(android.graphics.Color.WHITE)
            tvResultDesc.text = "得分: $score\n奖励解锁时间: $minutes 分钟"
            btnRetry.visibility = View.GONE
            handler.postDelayed({ unlockScreen(minutes * 60 * 1000L) }, 2500)
        } else {
            tvResultTitle.text = "再接再厉哦！"; tvResultTitle.setTextColor(android.graphics.Color.WHITE)
            tvResultDesc.text = "得分: $score\n至少需要答对 $required 题 (60分)"
            btnRetry.visibility = View.VISIBLE
        }
    }

    private fun reportToDingTalk(score: Int, passed: Boolean, correct: Int, total: Int, wrongs: List<String>) {
        Thread {
            try {
                val conn = URL("https://oapi.dingtalk.com/robot/send?access_token=e63f4fc767085735a440e559b77b4a599f41868f7928b9b9bedc6e65d4654de3").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"; conn.setRequestProperty("Content-Type", "application/json; charset=utf-8"); conn.doOutput = true
                
                val wrongsText = if (wrongs.isNotEmpty()) "\n错题:\n- " + wrongs.joinToString("\n- ") else ""
                val content = "【学习打卡】\n得分: $score\n正确率: $correct/$total\n结果: ${if(passed) "通过" else "未通过"}$wrongsText"
                
                val out = OutputStreamWriter(conn.outputStream, "UTF-8")
                out.write(JSONObject().put("msgtype", "text").put("text", JSONObject().put("content", content)).toString())
                out.flush(); out.close(); conn.responseCode
            } catch (e: Exception) {}
        }.start()
    }

    private fun showLockScreen() {
        countDownTimer?.cancel()
        lockContainer.visibility = View.VISIBLE; resultContainer.visibility = View.GONE; timerContainer.visibility = View.GONE
        updateParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, 0, 0)

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
            audioFocusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(audioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    private fun unlockScreen(ms: Long) {
        lockContainer.visibility = View.GONE; resultContainer.visibility = View.GONE; timerContainer.visibility = View.VISIBLE
        val size = (60 * resources.displayMetrics.density).toInt()
        updateParams(size, size, screenWidth - size, 200)
        startTimer(ms)

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
    }

    private fun updateParams(w: Int, h: Int, x: Int, y: Int) {
        params.width = w; params.height = h; params.x = x; params.y = y
        windowManager.updateViewLayout(floatingView, params)
    }

    private fun startTimer(ms: Long) {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(ms, 1000) {
            override fun onTick(rem: Long) {
                val s = ceil(rem / 1000.0).toInt()
                tvTimer.text = if (s >= 60) (s/60).toString() else s.toString()
            }
            override fun onFinish() { startQuiz() }
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
                        params.x = ix + dx; params.y = iy + dy
                        windowManager.updateViewLayout(floatingView, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (moved) {
                            val targetX = if (params.x + v.width / 2 < screenWidth / 2) 0 else screenWidth - v.width
                            ValueAnimator.ofInt(params.x, targetX).apply {
                                duration = 200
                                addUpdateListener { animation -> 
                                    params.x = animation.animatedValue as Int
                                    try { windowManager.updateViewLayout(floatingView, params) } catch (e: Exception) {}
                                }
                            }.start()
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
        val pi = android.app.PendingIntent.getService(applicationContext, 1, Intent(applicationContext, FloatingService::class.java),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) android.app.PendingIntent.FLAG_ONE_SHOT or android.app.PendingIntent.FLAG_IMMUTABLE else android.app.PendingIntent.FLAG_ONE_SHOT)
        (getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager).set(android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP, android.os.SystemClock.elapsedRealtime() + 1000, pi)
    }
}