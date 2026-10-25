package com.malinovka.widget.data

import kotlinx.serialization.Serializable

/** Откуда пришли данные для конкретного снимка. */
enum class SourceKind {
    /** Яндекс, live по GPS. */
    LIVE,

    /** Btrans: свежее плановое расписание онлайн. */
    SCHEDULE_ONLINE,

    /** Room-кэш последнего успешного обновления. */
    CACHE,

    /** Зашитый в APK дамп расписания. */
    SCHEDULE_DUMP,

    /** Данных нет вообще. */
    NONE;
}

/** Одна подача маршрута. Минуты и плановое время взаимно исключают друг друга. */
@Serializable
data class RouteEta(
    val route: String,
    val minutes: Int? = null,
    val scheduledAt: String? = null,
    val live: Boolean = false,
    val destination: String? = null,
)

/** Снимок по одному направлению. */
@Serializable
data class DirectionSnapshot(
    val stopKey: String,
    val etas: List<RouteEta>,
    val source: SourceKind,
    val updatedAtMs: Long,
)

/**
 * Конфигурация двух направлений виджета.
 *
 * Идентификаторы найдены исследованием (docs/data-sources.md):
 *  - Яндекс: страница остановки stop__<id>, прогноз вшит в HTML;
 *  - Btrans: slug страницы остановки и номер «платы» (доски) нужного направления,
 *    одинаковые для онлайн-запроса /schedule-data и для дампа расписания.
 *
 * Если на месте выяснится, что платформа направления подобрана неверно,
 * достаточно поменять yandexStopId / btransBoard здесь.
 */
data class StopConfig(
    val key: String,
    val title: String,
    val shortTitle: String,
    val yandexStopId: String,
    val btransSlug: String,
    val btransBoard: String,
)

object Stops {
    const val KEY_HOME_TO_METRO = "m7_to_city"
    const val KEY_METRO_TO_HOME = "metro_to_m7"

    val HOME_TO_METRO = StopConfig(
        key = KEY_HOME_TO_METRO,
        title = "Малиновка 7 → метро",
        shortTitle = "Мал. 7 → метро",
        yandexStopId = "10044891",
        btransSlug = "malinovka-7",
        btransBoard = "128962",
    )

    val METRO_TO_HOME = StopConfig(
        key = KEY_METRO_TO_HOME,
        title = "Ст. метро Малиновка → Малиновка 7",
        shortTitle = "метро → Мал. 7",
        yandexStopId = "10044918",
        btransSlug = "stancija-metro-malinovka",
        btransBoard = "029493",
    )

    val ALL = listOf(HOME_TO_METRO, METRO_TO_HOME)

    /** Маршруты по умолчанию; «114» фактически ходит как скорый «114с». */
    val DEFAULT_ROUTES = listOf("103", "114с", "150")

    fun byKey(key: String): StopConfig = ALL.first { it.key == key }
}
