package com.malinovka.widget.widget

import android.content.Context
import com.malinovka.widget.AppGraph
import com.malinovka.widget.data.Stops
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Оркестратор обновления виджета. Используется из:
 *  - кнопки «⟳» (force = true);
 *  - разблокировки экрана (force = false — сначала порог свежести);
 *  - загрузки системы и запуска приложения;
 *  - каждого тика мониторинга «Выхожу».
 */
object WidgetRefresher {

    /** Если оба снапшота моложе этого порога, сеть не трогаем. */
    private const val FRESHNESS_MS = 45_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Асинхронный запрос обновления (для ресиверов и кнопок). */
    fun request(context: Context, force: Boolean) {
        val app = context.applicationContext
        scope.launch {
            runCatching { refresh(app, force) }
                .onFailure { com.malinovka.widget.util.ErrorLog.log("refresh", it) }
        }
    }

    /** Обновление: порог свежести, затем параллельная загрузка обоих направлений. */
    suspend fun refresh(context: Context, force: Boolean) {
        val app = context.applicationContext
        AppGraph.dumpRepository.importIfNeeded()
        val settings = AppGraph.settings.snapshot()

        if (!force && isFreshEnough()) {
            repaintWidget(app)
            return
        }

        coroutineScope {
            Stops.ALL.forEach { cfg ->
                launch {
                    try {
                        AppGraph.repository.fetchStop(cfg, settings.routes)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        com.malinovka.widget.util.ErrorLog.log("refresh", e)
                    }
                }
            }
        }
        repaintWidget(app)
    }

    private suspend fun isFreshEnough(): Boolean {
        val now = System.currentTimeMillis()
        return Stops.ALL.all { cfg ->
            val snap = AppGraph.repository.storedSnapshot(cfg.key)
            snap != null && now - snap.updatedAtMs < FRESHNESS_MS
        }
    }
}
