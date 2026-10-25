package com.malinovka.widget.receivers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.malinovka.widget.widget.repaintWidget
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runCatching

/**
 * Граница светлая/тёмная тема — 06:00 и 18:00. Тема применяется только
 * при отрисовке, поэтому достаточно дешёвого неточного будильника,
 * который перерисует виджет без сети (устройство спит — сработает при пробуждении).
 */
object ThemeAlarm {

    private const val REQUEST_CODE = 1001

    fun ensureNext(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context)
        val next = nextBoundary(LocalDateTime.now())
        // setInexact + replace: один активный будильник, без разрешения SCHEDULE_EXACT_ALARM.
        am.setInexact(AlarmManager.RTC, next, pi)
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, ThemeAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    internal fun nextBoundary(now: LocalDateTime): Long {
        val date = now.toLocalDate()
        val today06 = LocalDateTime.of(date, LocalTime.of(6, 0))
        val today18 = LocalDateTime.of(date, LocalTime.of(18, 0))
        val next = when {
            now.isBefore(today06) -> today06
            now.isBefore(today18) -> today18
            else -> today06.plusDays(1)
        }
        return next.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}

class ThemeAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ThemeAlarm.ensureNext(context)
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                runCatching { repaintWidget(context) }
            } finally {
                result.finish()
            }
        }
    }
}
