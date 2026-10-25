package com.malinovka.widget.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Источник «свежее расписание»: btrans.by.
 *
 * POST https://minsk.btrans.by/schedule-data с телом {} и Referer страницы
 * остановки. Ответ: nearest[<тип ТС>][<маршрут>][<id платы>] =
 * [[минуты_до_подачи, графикая_минута], ...] — минуты уже посчитаны.
 * Это НЕ live-данные (доказано исследованием), поэтому live=false.
 * «Плата» (board) кодирует направление: используем только настроенную.
 */
class BtransArrivalsSource(
    private val client: OkHttpClient,
    private val json: Json,
) : ArrivalsSource {

    override suspend fun fetchArrivals(
        stop: StopConfig,
        routes: List<String>,
    ): List<RouteEta> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://minsk.btrans.by/schedule-data")
            .header("Referer", "https://minsk.btrans.by/ostanovka/${stop.btransSlug}")
            .header("User-Agent", UA)
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("HTTP ${response.code} от schedule-data Btrans")
            }
            val body = response.body.string()
            parseScheduleData(body, stop, routes)
        }
    }

    internal fun parseScheduleData(
        body: String,
        stop: StopConfig,
        routes: List<String>,
    ): List<RouteEta> {
        val nearest = json.parseToJsonElement(body).jsonObject["nearest"]?.jsonObject
            ?: error("Нет поля nearest в ответе Btrans")

        val result = mutableListOf<RouteEta>()
        for (route in routes) {
            val minutes = firstMinutes(nearest, route, stop.btransBoard)
                ?: continue
            result += RouteEta(
                route = route,
                minutes = minutes,
                live = false,
                destination = null,
            )
        }
        if (result.isEmpty()) {
            error("Btrans не вернул ни одного маршрута из ${routes.joinToString()}")
        }
        return result
    }

    /**
     * Ближайшая подача (минуты >= 0) маршрута. Только настроенная плата
     * направления: усреднение с чужими платами дало бы встречное направление.
     */
    private fun firstMinutes(
        nearest: kotlinx.serialization.json.JsonObject,
        route: String,
        preferredBoard: String,
    ): Int? {
        for (transportType in listOf("avtobus", "trollejbus", "tram")) {
            val byRoute = nearest[transportType]?.jsonObject ?: continue
            val routeEntry = byRoute.keys.firstOrNull { it.equals(route, ignoreCase = true) }
                ?.let { byRoute[it] }?.jsonObject ?: continue
            return routeEntry[preferredBoard]?.let { minutesFromBoard(it) }
        }
        return null
    }

    private fun minutesFromBoard(element: kotlinx.serialization.json.JsonElement): Int? {
        val pairs = element as? kotlinx.serialization.json.JsonArray ?: return null
        for (pair in pairs) {
            val arr = pair as? kotlinx.serialization.json.JsonArray ?: continue
            if (arr.size >= 2) {
                val minutes = arr[0].jsonPrimitive.content.toIntOrNull() ?: continue
                if (minutes >= 0) return minutes
            }
        }
        return null
    }

    private companion object {
        const val UA =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126 Mobile Safari/537.36"
    }
}
