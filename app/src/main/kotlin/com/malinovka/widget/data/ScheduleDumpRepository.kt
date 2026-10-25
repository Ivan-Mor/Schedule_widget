package com.malinovka.widget.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Формат assets/schedule.json (генерируется research/generate_schedule_dump.py). */
@Serializable
data class ScheduleDump(
    val generated: String,
    val stops: List<DumpStop> = emptyList(),
)

@Serializable
data class DumpStop(
    val id: String,
    val label: String,
    val routes: List<DumpRoute> = emptyList(),
)

@Serializable
data class DumpRoute(
    val route: String,
    val board: String,
    val destination: String,
    val weekday: List<Int> = emptyList(),
    val weekend: List<Int> = emptyList(),
)

/**
 * Последний рубеж цепочки: плановое расписание, зашитое в APK.
 * При первом запуске импортируется в Room, дальше подача считается локально:
 * ближайшее время >= сейчас, иначе первый рейс «завтра» (+24 ч).
 */
class ScheduleDumpRepository(
    private val context: Context,
    private val scheduleDao: com.malinovka.widget.data.db.ScheduleDao,
    private val settings: SettingsRepository,
    private val json: Json,
) {

    /** In-memory маркер, чтобы не трогать assets/DataStore на каждой перерисовке. */
    @Volatile
    private var importedMarker: String? = null

    private val importMutex = Mutex()

    suspend fun importIfNeeded() {
        if (importedMarker != null) return
        importMutex.withLock {
            if (importedMarker != null) return
            try {
                val text = context.assets.open("schedule.json")
                    .bufferedReader().use { it.readText() }
                val dump = json.decodeFromString<ScheduleDump>(text)
                if (settings.scheduleImportedFor(dump.generated)) {
                    importedMarker = dump.generated
                    return
                }

                val rows = mutableListOf<com.malinovka.widget.data.db.ScheduleEntity>()
                for (stop in dump.stops) {
                    for (route in stop.routes) {
                        // Берём только те платы, что настроены как направления виджета.
                        val target = Stops.ALL.firstOrNull {
                            it.btransSlug == stop.id && it.btransBoard == route.board
                        } ?: continue
                        rows += route.weekday.map {
                            entity(target.key, route, dayType = DAY_WEEKDAY, minute = it)
                        }
                        rows += route.weekend.map {
                            entity(target.key, route, dayType = DAY_WEEKEND, minute = it)
                        }
                    }
                }
                scheduleDao.clear()
                if (rows.isNotEmpty()) {
                    scheduleDao.insertAll(rows)
                }
                settings.markScheduleImported(dump.generated)
                importedMarker = dump.generated
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                com.malinovka.widget.util.ErrorLog.log("schedule-import", e)
            }
        }
    }

    /** Плановые подачи маршрута: минуты до ближайшего рейса по дампу. */
    suspend fun nextArrivals(stop: StopConfig, routes: List<String>): List<RouteEta> {
        val now = LocalTime.now()
        val nowMinutes = now.hour * 60 + now.minute
        val dayType = dayTypeToday()
        val result = mutableListOf<RouteEta>()
        for (route in routes) {
            val times = scheduleDao.minutes(stop.key, route, dayType)
            if (times.isEmpty()) continue
            val next = times.firstOrNull { it >= nowMinutes } ?: (times.first() + 24 * 60)
            result += RouteEta(
                route = route,
                minutes = next - nowMinutes,
                live = false,
                destination = null,
            )
        }
        return result
    }

    private fun entity(
        stopKey: String,
        route: DumpRoute,
        dayType: String,
        minute: Int,
    ) = com.malinovka.widget.data.db.ScheduleEntity(
        stopKey = stopKey,
        route = route.route,
        board = route.board,
        destination = route.destination,
        dayType = dayType,
        minute = minute,
    )

    companion object {
        const val DAY_WEEKDAY = "weekday"
        const val DAY_WEEKEND = "weekend"

        fun dayTypeToday(date: LocalDate = LocalDate.now()): String =
            if (date.dayOfWeek == DayOfWeek.SATURDAY ||
                date.dayOfWeek == DayOfWeek.SUNDAY
            ) {
                DAY_WEEKEND
            } else {
                DAY_WEEKDAY
            }
    }
}
