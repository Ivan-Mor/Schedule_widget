package com.malinovka.widget.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

/**
 * Live-источник №1: Яндекс Карты.
 *
 * Прогноз сервер вшивает в HTML страницы остановки
 * https://yandex.by/maps/157/minsk/stops/stop__<id>/ — отдельный JSON-API
 * без браузерной сессии не отвечает (см. docs/data-sources.md).
 * Live-подача помечена классом `_live`; если GPS-данных по рейсу нет,
 * вместо минут показывается плановое время («19:31»).
 */
class YandexArrivalsSource(private val client: OkHttpClient) : ArrivalsSource {

    override suspend fun fetchArrivals(
        stop: StopConfig,
        routes: List<String>,
    ): List<RouteEta> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://yandex.by/maps/157/minsk/stops/stop__${stop.yandexStopId}/")
            .header("User-Agent", UA)
            .header("Accept-Language", "ru")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("HTTP ${response.code} от страницы остановки Яндекс")
            }
            val body = response.body.string()
            val parsed = parseStopPage(body)
            if (parsed.isEmpty()) {
                error("Пустой прогноз на странице остановки (сменилась вёрстка?)")
            }
            parsed.filter { eta ->
                routes.any { it.equals(eta.route, ignoreCase = true) }
            }
        }
    }

    internal fun parseStopPage(html: String): List<RouteEta> {
        val doc = Jsoup.parse(html)
        val result = mutableListOf<RouteEta>()
        for (item in doc.select("li.masstransit-vehicle-snippet-view")) {
            val route = item
                .selectFirst(".masstransit-vehicle-snippet-view__main-text")
                ?.text()?.trim().orEmpty()
            if (route.isEmpty()) continue

            val live = item.selectFirst(".masstransit-prognoses-view__title._live") != null
            val first = item
                .selectFirst(".masstransit-prognoses-view__title-text")
                ?.text()?.trim().orEmpty()
            val destination = item
                .selectFirst(".masstransit-vehicle-snippet-view__vehicle-bottom-info")
                ?.text()
                ?.let { DEST_PREFIX_RE.replaceFirst(it, "") }
                ?.trim(' ', '«', '»')
                ?.ifEmpty { null }

            result += parseArrival(route, first, live, destination)
        }
        return result
    }

    private fun parseArrival(
        route: String,
        text: String,
        live: Boolean,
        destination: String?,
    ): RouteEta = when {
        text.isEmpty() -> RouteEta(route = route, destination = destination)
        text.equals("прибывает", ignoreCase = true) ->
            RouteEta(route = route, minutes = 0, live = live, destination = destination)
        else -> MINUTES_RE.find(text)?.let { m ->
            RouteEta(
                route = route,
                minutes = m.groupValues[1].toInt(),
                live = live,
                destination = destination,
            )
        } ?: TIME_RE.find(text)?.let { m ->
            RouteEta(
                route = route,
                scheduledAt = "${m.groupValues[1]}:${m.groupValues[2]}",
                live = false,
                destination = destination,
            )
        } ?: RouteEta(route = route, destination = destination)
    }

    private companion object {
        const val UA =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126 Mobile Safari/537.36"
        val MINUTES_RE = Regex("""^(\d{1,2})\s*мин""")
        val TIME_RE = Regex("""^(\d{1,2}):(\d{2})$""")
        val DEST_PREFIX_RE = Regex("""^\s*до\s*""", RegexOption.IGNORE_CASE)
    }
}
