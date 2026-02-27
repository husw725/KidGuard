package com.example.floating

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent

class LockAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        // 辅助服务连接成功，这里代表系统激活了我们。
        // 我们利用这个不易被杀的服务来重新拉起我们的主锁屏服务。
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
}