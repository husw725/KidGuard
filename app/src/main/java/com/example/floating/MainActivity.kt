package com.example.floating

import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.ClipboardManager
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

    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var componentName: ComponentName
    private val CLOUD_URL = "https://gitee.com/husw725/codes/i3hokdrwm7el20xsnab5u13/raw?blob_name=gistfile1.txt"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        componentName = ComponentName(this, MyAdminReceiver::class.java)

        setupConfigView()

        findViewById<Button>(R.id.btn_start_floating).setOnClickListener {
            checkPermissionsAndStart()
        }

        findViewById<Button>(R.id.btn_copy_url).setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Gitee URL", CLOUD_URL)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "题库链接已复制，请在浏览器打开并复制全部内容", Toast.LENGTH_LONG).show()
        }

        findViewById<Button>(R.id.btn_paste_sync).setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (clipboard.hasPrimaryClip() && clipboard.primaryClip?.itemCount ?: 0 > 0) {
                val pasteData = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                if (pasteData.isNotEmpty()) {
                    Toast.makeText(this, "该功能暂不可用", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "剪贴板为空", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "剪贴板无内容", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btn_send_now).setOnClickListener {
            val report = QuestionBank.getDailyReport(this)
            Toast.makeText(this, "正在尝试发送实时报表...", Toast.LENGTH_SHORT).show()
            if (report != null) {
                Thread {
                    val success = FloatingService.reportToDingTalkRaw(report)
                    runOnUiThread {
                        if (success) {
                            Toast.makeText(this, "实时日报已成功发送！", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "发送失败，请检查网络", Toast.LENGTH_SHORT).show()
                        }
                    }
                }.start()
            } else {
                Toast.makeText(this, "当前暂无学习记录可发送", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupConfigView() {
        val etTotalQuestions = findViewById<EditText>(R.id.et_total_questions)
        etTotalQuestions.setText(QuestionBank.getTotalQuestionConfig(this).toString())

        findViewById<Button>(R.id.btn_save_config).setOnClickListener {
            val inputCount = etTotalQuestions.text.toString().toIntOrNull()
            if (inputCount != null && inputCount >= 20) {
                QuestionBank.setTotalQuestionConfig(this, inputCount)
                Toast.makeText(this, "设置已保存：每次 $inputCount 题", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "请输入大于等于20的数字", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context, accessibilityService: Class<*>): Boolean {
        val expectedComponentName = ComponentName(context, accessibilityService)
        val enabledServicesSetting = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledService = ComponentName.unflattenFromString(componentNameString)
            if (enabledService != null && enabledService == expectedComponentName) return true
        }
        return false
    }

    private fun checkPermissionsAndStart() {
        // Android 13+ 前台服务通知需运行时授权，否则通知不显示、部分 ROM 更易回收服务
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        if (!devicePolicyManager.isAdminActive(componentName)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "请激活设备管理器以防应用被卸载。")
            }
            startActivity(intent)
            return
        }
        if (!isAccessibilityServiceEnabled(this, LockAccessibilityService::class.java)) {
            Toast.makeText(this, "请在无障碍设置中开启【小欣学习】以防被强杀", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        startFloatingService()
    }

    private fun startFloatingService() {
        val intent = Intent(this, FloatingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}