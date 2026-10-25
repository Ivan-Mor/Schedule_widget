package com.malinovka.widget.data

/**
 * Источник данных о подачах на остановку.
 * Реализация кидает исключение при любой проблеме — цепочка фолбэков
 * ловит его и пробует следующий источник (см. ArrivalsRepository).
 */
interface ArrivalsSource {
    suspend fun fetchArrivals(stop: StopConfig, routes: List<String>): List<RouteEta>
}
