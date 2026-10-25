package com.malinovka.widget

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.malinovka.widget.receivers.ThemeAlarm
import com.malinovka.widget.widget.WidgetRefresher

class App : Application() {

    private val screenOnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Порог свежести обрабатывается внутри: если данные моложе 45 с,
            // сетевых запросов не будет — только перерисовка.
            WidgetRefresher.request(context, force = false)
        }
    }

    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)

        // ACTION_USER_PRESENT (разблокировка) не доставляется в manifest-ресиверы
        // с API 26, поэтому регистрируем динамически: приёмник живёт,
        // пока жив процесс приложения. Порог свежести внутри request()
        // защищает от лишних запросов, если данные моложе 45 секунд.
        ContextCompat.registerReceiver(
            this,
            screenOnReceiver,
            IntentFilter(Intent.ACTION_USER_PRESENT),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        ThemeAlarm.ensureNext(this)
    }
}
