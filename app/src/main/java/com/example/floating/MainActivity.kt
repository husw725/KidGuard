package com.example.floating

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val REQUEST_CODE_OVERLAY = 100
    private val REQUEST_CODE_ADMIN = 101
    
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var componentName: ComponentName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        QuestionBank.initAndSyncCloud(this)

        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        componentName = ComponentName(this, MyAdminReceiver::class.java)

        val etTotalQuestions = findViewById<EditText>(R.id.et_total_questions)
        val currentTotal = QuestionBank.getTotalQuestionConfig(this)
        etTotalQuestions.setText(currentTotal.toString())

        findViewById<Button>(R.id.btn_save_config).setOnClickListener {
            val inputCount = etTotalQuestions.text.toString().toIntOrNull()
            if (inputCount != null && inputCount >= 10) {
                QuestionBank.setTotalQuestionConfig(this, inputCount)
                Toast.makeText(this, "设置已保存：每次 $inputCount 题", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "请输入大于等于10的数字", Toast.LENGTH_SHORT).show()
                etTotalQuestions.setText(QuestionBank.getTotalQuestionConfig(this).toString())
            }
        }

        findViewById<Button>(R.id.btn_start_floating).setOnClickListener {
            checkPermissionsAndStart()
        }

        findViewById<Button>(R.id.btn_add_question).setOnClickListener {
            val input = findViewById<EditText>(R.id.et_question_input).text.toString().trim()
            if (input.isNotEmpty()) {
                val parts = input.split("|")
                if (parts.size == 6) {
                    try {
                        val text = parts[0]
                        val options = listOf(parts[1], parts[2], parts[3], parts[4])
                        val correctIndex = parts[5].toInt() - 1 // 转为 0-based index
                        if (correctIndex in 0..3) {
                            val q = Question(text, options, correctIndex)
                            QuestionBank.saveCustomQuestion(this, q)
                            Toast.makeText(this, "添加成功！", Toast.LENGTH_SHORT).show()
                            findViewById<EditText>(R.id.et_question_input).text.clear()
                        } else {
                            Toast.makeText(this, "正确选项序号必须是 1 到 4", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this, "格式解析错误，请确保最后一位是数字", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "格式错误！请确保用竖线分隔为6部分", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context, accessibilityService: Class<*>): Boolean {
        val expectedComponentName = ComponentName(context, accessibilityService)
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
            ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)

        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledService = ComponentName.unflattenFromString(componentNameString)
            if (enabledService != null && enabledService == expectedComponentName) {
                return true
            }
        }
        return false
    }

    private fun checkPermissionsAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return
        }

        if (!devicePolicyManager.isAdminActive(componentName)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "请激活设备管理器，以防止应用被意外卸载。")
            startActivity(intent)
            return
        }

        if (!isAccessibilityServiceEnabled(this, LockAccessibilityService::class.java)) {
            Toast.makeText(this, "为了防止应用被一键清理强杀，请在无障碍设置中开启【Floating App】服务", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            return
        }

        startFloatingService()
    }
    
    private fun startFloatingService() {
        val intent = Intent(this, FloatingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        
        val homeIntent = Intent(Intent.ACTION_MAIN)
        homeIntent.addCategory(Intent.CATEGORY_HOME)
        homeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(homeIntent)
        finish()
    }

    override fun onResume() {
        super.onResume()
    }
}