
package com.example.floating

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar
import kotlin.concurrent.thread

class ReportScheduler {
    companion object {
        // 约每 15 分钟轮询飞书一次（未锁屏时也能收到“锁定/暂停/留言”指令）
        fun scheduleFeishuPoll(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = PendingIntent.getBroadcast(
                context, 2, Intent(context, FeishuPollReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 60_000,
                AlarmManager.INTERVAL_FIFTEEN_MINUTES,
                pi
            )
        }

        fun scheduleDailyReport(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ReportBroadcastReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, 
                0, 
                intent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val calendar: Calendar = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                set(Calendar.HOUR_OF_DAY, 20)
                set(Calendar.MINUTE, 30)
                set(Calendar.SECOND, 0)
            }

            if (calendar.timeInMillis < System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        }
    }
}

class ReportBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.intent.action.BOOT_COMPLETED") {
             ReportScheduler.scheduleDailyReport(context)
        } else {
            // Re-schedule for next day because setExact is a one-time alarm
            ReportScheduler.scheduleDailyReport(context)
            FloatingService.sendDailyReport(context)
        }
    }
}

// 未锁屏时轮询飞书指令（锁屏时由 FloatingService 内的循环负责，更勤）
class FeishuPollReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext
        thread {
            val cmds = FeishuClient.pollNewMessages(ctx)
            for (text in cmds) applyHeadless(ctx, FeishuClient.parseCommand(text))
        }
    }

    private fun applyHeadless(ctx: Context, c: FeishuClient.Command) {
        val p = ctx.getSharedPreferences(FeishuClient.PREFS, Context.MODE_PRIVATE)
        when (c) {
            is FeishuClient.Command.PauseLock ->
                p.edit().putLong(FeishuClient.KEY_PAUSE_UNTIL, System.currentTimeMillis() + c.minutes * 60_000L).apply()
            is FeishuClient.Command.LockNow -> {
                p.edit().putLong(FeishuClient.KEY_PAUSE_UNTIL, 0L).apply()   // 取消暂停
                val si = Intent(ctx, FloatingService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(si) else ctx.startService(si)
            }
            is FeishuClient.Command.MessageToChild ->
                p.edit().putString(FeishuClient.KEY_PENDING_MSG, c.text).apply()   // 下次锁屏展示
            is FeishuClient.Command.UnlockNow -> { /* 未锁屏，无需解锁 */ }
        }
    }
}
