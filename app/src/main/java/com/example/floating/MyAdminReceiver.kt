package com.example.floating

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import kotlin.concurrent.thread

class MyAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        Toast.makeText(context, "设备管理器已激活", Toast.LENGTH_SHORT).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "取消激活后，应用将失去保护和锁屏能力。"
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Toast.makeText(context, "设备管理器已取消激活", Toast.LENGTH_SHORT).show()
        thread { FeishuClient.sendText("⚠️ 小欣关掉了「设备管理器」，App 可能被卸载，请留意。") }
    }
}