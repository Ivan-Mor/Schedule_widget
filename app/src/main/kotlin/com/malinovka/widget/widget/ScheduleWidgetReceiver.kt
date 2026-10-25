package com.malinovka.widget.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll

class ScheduleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: ScheduleWidget = ScheduleWidget()
}

/** Перерисовать виджет по текущим снапшотам (сеть не трогаем). */
suspend fun repaintWidget(context: Context) {
    ScheduleWidget().updateAll(context)
}
