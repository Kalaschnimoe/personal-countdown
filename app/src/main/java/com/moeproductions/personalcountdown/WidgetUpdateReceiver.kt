// WidgetUpdateReceiver.kt
package com.moeproductions.personalcountdown

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class WidgetUpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION, Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_DATE_CHANGED -> {
                // Widget sofort refreshen
                CoroutineScope(Dispatchers.Default).launch {
                    CountdownWidget().updateAll(context)
                }
                // Nächsten Minutentick planen
                scheduleNextMinute(context)
            }
        }
    }

    companion object {
        const val ACTION = "com.moeproductions.personalcountdown.UPDATE_WIDGET"
        private const val ACTION_UPDATE_WIDGET = "com.moeproductions.personalcountdown.action.UPDATE_WIDGET"
        private const val REQUEST_CODE = 1001
        fun scheduleNextMinute(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            cancelAlarm(context)

            // nächste volle Minute
            val cal = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.MINUTE, 1)
            }

            val triggerAt = cal.timeInMillis
            val pi = getPendingIntent(context)
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                // Weicher Fallback, wenn der Nutzer die Berechtigung für exakte Alarme entzogen hat.
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        }

        private fun getPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, WidgetUpdateReceiver::class.java).apply {
                action = ACTION_UPDATE_WIDGET
            }
            return PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
        fun cancelAlarm(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = getPendingIntent(context)
            am.cancel(pi)
        }
        fun triggerNow(context: Context) {
            context.sendBroadcast(
                Intent(
                    context, WidgetUpdateReceiver::class.java
                ).setAction(ACTION)
            )
        }
    }
}
