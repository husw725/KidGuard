
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
                set(Calendar.MINUTE, 0)
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

    // 唤醒前台服务；extra 用来告诉服务需要重新评估哪种状态（次数上限 / 暂停期）
    private fun wake(ctx: Context, extra: String? = null) {
        val si = Intent(ctx, FloatingService::class.java)
        if (extra != null) si.putExtra(extra, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(si) else ctx.startService(si)
    }

    private fun applyHeadless(ctx: Context, c: FeishuClient.Command) {
        val p = ctx.getSharedPreferences(FeishuClient.PREFS, Context.MODE_PRIVATE)
        when (c) {
            is FeishuClient.Command.PauseLock -> {
                p.edit().putLong(FeishuClient.KEY_PAUSE_UNTIL, System.currentTimeMillis() + c.minutes * 60_000L).apply()
                thread { FeishuClient.sendText("✅ 已暂停锁屏 ${c.minutes} 分钟") }
                wake(ctx, "reeval_pause")   // 可能正锁着（消息被本轮询抢到）：立即放行
            }
            is FeishuClient.Command.LockNow -> {
                p.edit().putLong(FeishuClient.KEY_PAUSE_UNTIL, 0L).apply()   // 取消暂停
                wake(ctx)
            }
            is FeishuClient.Command.MessageToChild ->
                p.edit().putString(FeishuClient.KEY_PENDING_MSG, c.text).apply()   // 下次锁屏展示
            is FeishuClient.Command.UnlockNow -> {
                // 45s 锁屏轮询没抢到时由这里兜底：按“暂停到期”处理并唤醒前台
                p.edit().putLong(FeishuClient.KEY_PAUSE_UNTIL, System.currentTimeMillis() + c.minutes * 60_000L).apply()
                thread { FeishuClient.sendText("✅ 已远程解锁 ${c.minutes} 分钟") }
                wake(ctx, "reeval_pause")
            }
            is FeishuClient.Command.SetUnlockLimit -> {
                QuestionBank.setDailyUnlockLimit(ctx, c.limit)
                thread { FeishuClient.sendText("✅ 每日可答题解锁次数已设为 ${c.limit} 次") }
            }
            is FeishuClient.Command.Help -> thread { FeishuClient.sendText(FeishuClient.HELP_TEXT) }
            is FeishuClient.Command.GrantExtra -> {
                QuestionBank.addTodayBonus(ctx, c.times)
                thread { FeishuClient.sendText("✅ 今天临时增加 ${c.times} 次答题机会") }
                wake(ctx, "reeval_limit")   // 若正卡在用完屏，立即切到答题
            }
            is FeishuClient.Command.ResetUnlocks -> {
                QuestionBank.resetTodayUnlocks(ctx)
                thread { FeishuClient.sendText("✅ 今日解锁次数已重置") }
                wake(ctx, "reeval_limit")
            }
        }
    }
}
