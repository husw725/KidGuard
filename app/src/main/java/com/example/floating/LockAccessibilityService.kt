package com.example.floating

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent

class LockAccessibilityService : AccessibilityService() {

    companion object {
        private var instance: LockAccessibilityService? = null

        fun pressHome() {
            instance?.let {
                it.performGlobalAction(GLOBAL_ACTION_HOME)
                android.widget.Toast.makeText(it, "时间到，正在返回桌面...", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        // 辅助服务连接成功，这里代表系统激活了我们。
        val serviceIntent = Intent(this, FloatingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 不需要处理具体的事件，仅用作防杀保活
    }

    override fun onInterrupt() {
        // 服务被中断
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
}
