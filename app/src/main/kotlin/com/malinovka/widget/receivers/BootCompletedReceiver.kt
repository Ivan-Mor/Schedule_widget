package com.malinovka.widget.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.malinovka.widget.widget.WidgetRefresher

/** После перезагрузки: перепланировать будильник темы и обновить виджет. */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        ThemeAlarm.ensureNext(context)
        WidgetRefresher.request(context, force = false)
    }
}
