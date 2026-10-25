package com.malinovka.widget.widget

import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionCallback
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.clickable
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.malinovka.widget.AppGraph
import com.malinovka.widget.data.DirectionSnapshot
import com.malinovka.widget.data.RouteEta
import com.malinovka.widget.data.SourceKind
import com.malinovka.widget.data.Stops
import com.malinovka.widget.service.MonitoringService
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Виджет 4×2: две колонки (Малиновка 7 → метро / метро → Малиновка 7)
 * и общий футер: статус данных, время обновления, кнопки «⟳» и «Выхожу».
 * Тема выбирается при каждой отрисовке по времени: тёмная 18:00–06:00.
 *
 * provideGlance читает только Room и настройки — сеть трогают
 * WidgetRefresher и MonitoringService, потом просто просят перерисовку.
 */
class ScheduleWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        AppGraph.dumpRepository.importIfNeeded()
        val settings = AppGraph.settings.snapshot()
        val snapshots = Stops.ALL.associate { cfg ->
            cfg.key to AppGraph.repository.storedSnapshot(cfg.key)
        }
        provideContent {
            val colors = if (isDarkNow()) DarkColors else LightColors
            WidgetContent(
                colors = colors,
                routes = settings.routes,
                snapshots = snapshots,
            )
        }
    }

    companion object {
        fun isDarkNow(time: LocalTime = LocalTime.now()): Boolean {
            val minuteOfDay = time.hour * 60 + time.minute
            return minuteOfDay >= 18 * 60 || minuteOfDay < 6 * 60
        }
    }
}

private val FOOTER_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private data class WidgetColors(
    val background: Int,
    val card: Int,
    val textPrimary: Int,
    val textDim: Int,
    val accent: Int,
    val live: Int,
)

private val DarkColors = WidgetColors(
    background = AndroidColor.parseColor("#F01E1F22"),
    card = AndroidColor.parseColor("#F02A2B2F"),
    textPrimary = AndroidColor.parseColor("#FFF2F2F2"),
    textDim = AndroidColor.parseColor("#FF9AA0A6"),
    accent = AndroidColor.parseColor("#FF7AB8FF"),
    live = AndroidColor.parseColor("#FF4ADE80"),
)

private val LightColors = WidgetColors(
    background = AndroidColor.parseColor("#F2FFFFFF"),
    card = AndroidColor.parseColor("#FFFFFFFF"),
    textPrimary = AndroidColor.parseColor("#FF1B1D20"),
    textDim = AndroidColor.parseColor("#FF5B6169"),
    accent = AndroidColor.parseColor("#FF1D4ED8"),
    live = AndroidColor.parseColor("#FF15803D"),
)

@Composable
private fun WidgetContent(
    colors: WidgetColors,
    routes: List<String>,
    snapshots: Map<String, DirectionSnapshot?>,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(colors.background))
            .padding(8.dp),
    ) {
        Row(
            modifier = GlanceModifier.defaultWeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BlockColumn(
                modifier = GlanceModifier.defaultWeight(),
                blockTitle = Stops.HOME_TO_METRO.shortTitle,
                snapshot = snapshots[Stops.HOME_TO_METRO.key],
                routes = routes,
                colors = colors,
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
            BlockColumn(
                modifier = GlanceModifier.defaultWeight(),
                blockTitle = Stops.METRO_TO_HOME.shortTitle,
                snapshot = snapshots[Stops.METRO_TO_HOME.key],
                routes = routes,
                colors = colors,
            )
        }
        Spacer(modifier = GlanceModifier.padding(3.dp))
        Footer(colors = colors, snapshots = snapshots)
    }
}

/** Одна колонка-направление. Вес внутри родительского Row передаётся снаружи. */
@Composable
private fun BlockColumn(
    modifier: GlanceModifier,
    blockTitle: String,
    snapshot: DirectionSnapshot?,
    routes: List<String>,
    colors: WidgetColors,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(ColorProvider(colors.card))
            .cornerRadius(10.dp)
            .padding(6.dp),
    ) {
        Text(
            text = blockTitle,
            style = TextStyle(
                color = ColorProvider(colors.textDim),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
        for (route in routes) {
            val eta = snapshot?.etas?.firstOrNull {
                it.route.equals(route, ignoreCase = true)
            }
            RouteRow(route = route, eta = eta, colors = colors)
        }
    }
}

@Composable
private fun RouteRow(route: String, eta: RouteEta?, colors: WidgetColors) {
    val value = when {
        eta == null -> "—"
        eta.minutes != null -> "${eta.minutes} мин"
        eta.scheduledAt != null -> eta.scheduledAt
        else -> "?"
    }
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = route,
            style = TextStyle(
                color = ColorProvider(colors.textPrimary),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            ),
            modifier = GlanceModifier.defaultWeight(),
            maxLines = 1,
        )
        if (eta?.live == true) {
            Text(
                text = "●",
                style = TextStyle(
                    color = ColorProvider(colors.live),
                    fontSize = 9.sp,
                ),
            )
            Spacer(modifier = GlanceModifier.width(2.dp))
        }
        Text(
            text = value,
            style = TextStyle(
                color = ColorProvider(
                    if (eta?.live == true) colors.live else colors.textPrimary
                ),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.End,
            ),
        )
    }
}

@Composable
private fun Footer(
    colors: WidgetColors,
    snapshots: Map<String, DirectionSnapshot?>,
) {
    val statuses = snapshots.values.filterNotNull()
    val statusText = when {
        statuses.isEmpty() || statuses.any { it.source == SourceKind.NONE } -> "нет данных"
        statuses.all { it.source == SourceKind.LIVE } -> "live"
        statuses.any { it.source == SourceKind.CACHE } -> {
            val ageMin = (System.currentTimeMillis() -
                (statuses.minOfOrNull { it.updatedAtMs } ?: 0L)) / 60_000
            "кэш · $ageMin мин"
        }
        else -> "по расписанию"
    }
    val updated = statuses.maxOfOrNull { it.updatedAtMs }?.let { ms ->
        FOOTER_TIME.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
    } ?: "--:--"

    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$statusText · $updated",
            style = TextStyle(color = ColorProvider(colors.textDim), fontSize = 10.sp),
            modifier = GlanceModifier.defaultWeight(),
            maxLines = 1,
        )
        Text(
            text = "⟳",
            style = TextStyle(
                color = ColorProvider(colors.textPrimary),
                fontSize = 14.sp,
            ),
            modifier = GlanceModifier
                .background(ColorProvider(colors.card))
                .cornerRadius(8.dp)
                .clickable(actionRunCallback<RefreshAction>())
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
        Spacer(modifier = GlanceModifier.width(5.dp))
        Text(
            text = "Выхожу",
            style = TextStyle(
                color = ColorProvider(colors.textPrimary),
                fontSize = 11.sp,
            ),
            modifier = GlanceModifier
                .background(ColorProvider(colors.accent))
                .cornerRadius(8.dp)
                .clickable(actionRunCallback<LeavingAction>())
                .padding(horizontal = 7.dp, vertical = 2.dp),
        )
    }
}

/** Кнопка «⟳»: принудительное обновление обоих направлений. */
class RefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        WidgetRefresher.request(context, force = true)
    }
}

/** Кнопка «Выхожу»: запуск мониторинга по направлению из настроек. */
class LeavingAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        MonitoringService.start(context)
    }
}
