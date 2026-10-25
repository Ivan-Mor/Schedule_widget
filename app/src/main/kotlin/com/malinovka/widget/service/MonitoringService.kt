package com.malinovka.widget.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.malinovka.widget.AppGraph
import com.malinovka.widget.R
import com.malinovka.widget.data.Stops
import com.malinovka.widget.widget.repaintWidget
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Мониторинг по кнопке «Выхожу»: 15 минут следим за направлением из настроек,
 * опрос каждые 30 секунд по цепочке источников (live → расписание → …).
 * Каждый маршрут присылает одно уведомление, когда подача попадает
 * в настраиваемый порог «пора». Останов: по таймеру, когда все маршруты
 * подали, или по кнопке в уведомлении.
 */
class MonitoringService : LifecycleService() {

    private var monitorJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val notifiedRoutes = mutableSetOf<String>()
    private var startedAtMs = 0L

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            finishMonitoring()
            return START_NOT_STICKY
        }
        startMonitoring()
        return START_NOT_STICKY
    }

    private fun startMonitoring() {
        if (monitorJob?.isActive == true) return // уже следим — рестарт не нужен
        startedAtMs = System.currentTimeMillis()
        notifiedRoutes.clear()

        wakeLock = powerManager().newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "malinovka:leaving-monitor",
        ).also { it.acquire(DURATION_MS + 60_000L) }

        ServiceCompat.startForeground(
            this,
            ONGOING_NOTIFICATION_ID,
            ongoingNotification(remainingMs = DURATION_MS),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )

        monitorJob = lifecycleScope.launch {
            while (true) {
                val settings = AppGraph.settings.snapshot()
                val elapsed = System.currentTimeMillis() - startedAtMs
                val remaining = DURATION_MS - elapsed
                if (remaining <= 0) {
                    finishMonitoring()
                    return@launch
                }
                tick(settings, remaining)
                val selected = settings.routes.map { it.lowercase() }.toSet()
                if (selected.isNotEmpty() && selected.all { it in notifiedRoutes }) {
                    finishMonitoring()
                    return@launch
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun tick(
        settings: com.malinovka.widget.data.SettingsState,
        remainingMs: Long,
    ) {
        val stop = Stops.byKey(settings.leavingStopKey)
        val snapshot = try {
            AppGraph.repository.fetchStop(stop, settings.routes)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            com.malinovka.widget.util.ErrorLog.log("monitoring-tick", e)
            null
        }

        try {
            repaintWidget(this@MonitoringService)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            com.malinovka.widget.util.ErrorLog.log("monitoring-repaint", e)
        }

        if (snapshot != null) {
            for (eta in snapshot.etas) {
                val minutes = eta.minutes ?: continue
                val routeKey = eta.route.lowercase()
                if (minutes in 0..settings.thresholdMinutes && routeKey !in notifiedRoutes) {
                    notifyRouteArrival(eta.route, minutes, eta.destination)
                    notifiedRoutes += routeKey
                }
            }
        }

        notifyManager().notify(
            ONGOING_NOTIFICATION_ID,
            ongoingNotification(remainingMs),
        )
    }

    private fun finishMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        notifyManager().cancel(ONGOING_NOTIFICATION_ID)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // --- Уведомления ---

    private fun createChannels() {
        val manager = notifyManager()
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ONGOING,
                getString(R.string.monitoring_channel_name),
                NotificationManager.IMPORTANCE_MIN,
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERTS,
                getString(R.string.alerts_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            )
        )
    }

    private fun ongoingNotification(remainingMs: Long): Notification {
        val minutesLeft = (remainingMs / 60_000).toInt().coerceAtLeast(0)
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, MonitoringService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_bus)
            .setContentTitle(getString(R.string.monitoring_ongoing))
            .setContentText("ещё $minutesLeft мин · ${notifiedRoutes.size} подано")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, getString(R.string.monitoring_stop), stopIntent)
            .build()
    }

    private fun notifyRouteArrival(route: String, minutes: Int, destination: String?) {
        if (ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return // без разрешения тихо пропускаем, мониторинг продолжает работать
        }
        val text = buildString {
            append("через $minutes мин — пора выходить")
            destination?.let { append(" · до «").append(it).append('»') }
        }
        notifyManager().notify(
            route.hashCode(),
            NotificationCompat.Builder(this, CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_stat_bus)
                .setContentTitle(route)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build(),
        )
    }

    private fun notifyManager(): NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun powerManager(): PowerManager =
        getSystemService(Context.POWER_SERVICE) as PowerManager

    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.malinovka.widget.STOP_MONITORING"
        private const val CHANNEL_ONGOING = "monitoring"
        private const val CHANNEL_ALERTS = "alerts"
        private const val ONGOING_NOTIFICATION_ID = 1
        private const val DURATION_MS = 15 * 60_000L
        private const val POLL_INTERVAL_MS = 30_000L

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, MonitoringService::class.java),
            )
        }
    }
}
