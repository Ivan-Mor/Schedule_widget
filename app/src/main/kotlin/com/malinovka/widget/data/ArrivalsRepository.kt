package com.malinovka.widget.data

import com.malinovka.widget.data.db.ScheduleDao
import com.malinovka.widget.data.db.SnapshotDao
import com.malinovka.widget.data.db.SnapshotEntity
import com.malinovka.widget.util.ErrorLog
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Сколько секунд кэш считается «живым» для показа вместо новых данных. */
const val CACHE_TTL_MS: Long = 10 * 60 * 1000

/**
 * Цепочка источников по ТЗ:
 * Яндекс (live) → Btrans (свежее расписание) → кэш Room (10 мин) → дамп расписания.
 * Сбой любого звена не ломает виджет: пробуем следующее, ошибку пишем в журнал.
 */
class ArrivalsRepository(
    private val yandex: YandexArrivalsSource,
    private val btrans: BtransArrivalsSource,
    private val dump: ScheduleDumpRepository,
    private val snapshotDao: SnapshotDao,
    private val scheduleDao: ScheduleDao,
    private val json: Json,
) {

    suspend fun fetchStop(stop: StopConfig, routes: List<String>): DirectionSnapshot {
        if (routes.isEmpty()) {
            return store(stop, emptyList(), SourceKind.NONE)
        }

        // 1. Яндекс — live.
        tryOrLog("yandex") { yandex.fetchArrivals(stop, routes) }
            ?.let { return store(stop, it, SourceKind.LIVE) }

        // 2. Btrans — свежее расписание онлайн.
        tryOrLog("btrans") { btrans.fetchArrivals(stop, routes) }
            ?.let { return store(stop, it, SourceKind.SCHEDULE_ONLINE) }

        // 3. Кэш последнего удачного обновления.
        val cached = snapshotDao.get(stop.key)
        if (cached != null && System.currentTimeMillis() - cached.updatedAtMs <= CACHE_TTL_MS) {
            return DirectionSnapshot(
                stopKey = stop.key,
                etas = decode(cached.etasJson),
                source = SourceKind.CACHE,
                updatedAtMs = cached.updatedAtMs,
            )
        }

        // 4. Зашитый дамп расписания.
        val dumpEtas = tryOrLog("dump") { dump.nextArrivals(stop, routes) }
        if (!dumpEtas.isNullOrEmpty()) {
            return store(stop, dumpEtas, SourceKind.SCHEDULE_DUMP)
        }

        // Совсем ничего: показываем последний снимок каким он есть.
        val stale = cached ?: SnapshotEntity(stop.key, "[]", SourceKind.NONE.name, 0)
        return DirectionSnapshot(
            stopKey = stop.key,
            etas = decode(stale.etasJson),
            source = SourceKind.NONE,
            updatedAtMs = stale.updatedAtMs,
        )
    }

    /**
     * Провести запрос источника: ошибки источника логируются и превращаются
     * в null (цепочка идёт дальше), но отмена корутины пробрасывается.
     */
    private suspend fun <T> tryOrLog(tag: String, block: suspend () -> T): T? =
        try {
            block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            ErrorLog.log(tag, e)
            null
        }

    /** Сохраняет снимок в Room и возвращает его. */
    private suspend fun store(
        stop: StopConfig,
        etas: List<RouteEta>,
        source: SourceKind,
    ): DirectionSnapshot {
        val snapshot = DirectionSnapshot(
            stopKey = stop.key,
            etas = etas,
            source = source,
            updatedAtMs = System.currentTimeMillis(),
        )
        tryOrLog("snapshot-store") {
            snapshotDao.put(
                SnapshotEntity(
                    stopKey = snapshot.stopKey,
                    etasJson = encode(snapshot.etas),
                    source = snapshot.source.name,
                    updatedAtMs = snapshot.updatedAtMs,
                )
            )
        }
        return snapshot
    }

    suspend fun storedSnapshot(stopKey: String): DirectionSnapshot? {
        val entity = snapshotDao.get(stopKey) ?: return null
        return DirectionSnapshot(
            stopKey = entity.stopKey,
            etas = decode(entity.etasJson),
            source = runCatching { SourceKind.valueOf(entity.source) }
                .getOrDefault(SourceKind.NONE),
            updatedAtMs = entity.updatedAtMs,
        )
    }

    private fun encode(etas: List<RouteEta>): String =
        json.encodeToString(ListSerializer(RouteEta.serializer()), etas)

    private fun decode(text: String): List<RouteEta> = runCatching {
        json.decodeFromString(ListSerializer(RouteEta.serializer()), text)
    }.getOrDefault(emptyList())
}
