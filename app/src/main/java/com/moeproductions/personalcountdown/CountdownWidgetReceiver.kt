package com.moeproductions.personalcountdown

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class CountdownWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CountdownWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleMinuteUpdates(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelMinuteUpdates(context)
    }

    private fun scheduleMinuteUpdates(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context)

        val firstTrigger = System.currentTimeMillis() + 1_000L

        // B) Best-Effort: keine Sonderberechtigung nötig
        am.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            firstTrigger,
            60_000L, // 1 Minute
            pi
        )

        // A) Exakt: nur wenn du SCHEDULE_EXACT_ALARM erlaubst, nacheinander reschedulen (siehe unten Variante Exact)
        // -> Alternativ (exakt) in WidgetUpdateReceiver nach onReceive erneut setExact schedulen.
    }

    private fun cancelMinuteUpdates(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, WidgetUpdateReceiver::class.java).apply {
            action = "com.moeproductions.personalcountdown.UPDATE_WIDGET"
        }
        return PendingIntent.getBroadcast(
            context,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
