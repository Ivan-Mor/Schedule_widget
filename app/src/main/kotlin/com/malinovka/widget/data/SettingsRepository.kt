package com.malinovka.widget.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class SettingsState(
    val leavingStopKey: String = Stops.KEY_HOME_TO_METRO,
    val routes: List<String> = Stops.DEFAULT_ROUTES,
    val thresholdMinutes: Int = 5,
)

/** Настройки приложения: направление «Выхожу», маршруты, порог «пора». */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val LEAVING_STOP = stringPreferencesKey("leaving_stop_key")
        val ROUTES = stringSetPreferencesKey("routes")
        val THRESHOLD = intPreferencesKey("threshold_minutes")
        val SCHEDULE_IMPORTED = stringPreferencesKey("schedule_imported_generated")
    }

    val state: Flow<SettingsState> = context.dataStore.data.map { prefs ->
        SettingsState(
            leavingStopKey = prefs[Keys.LEAVING_STOP] ?: Stops.KEY_HOME_TO_METRO,
            routes = (prefs[Keys.ROUTES] ?: Stops.DEFAULT_ROUTES.toSet())
                .sortedBy { Stops.DEFAULT_ROUTES.indexOf(it).takeIf { i -> i >= 0 } ?: it.length },
            thresholdMinutes = prefs[Keys.THRESHOLD] ?: 5,
        )
    }

    suspend fun snapshot(): SettingsState = state.first()

    suspend fun setLeavingStop(stopKey: String) {
        context.dataStore.edit { it[Keys.LEAVING_STOP] = stopKey }
    }

    suspend fun toggleRoute(route: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.ROUTES] ?: Stops.DEFAULT_ROUTES.toSet()
            prefs[Keys.ROUTES] =
                if (route in current) current - route else current + route
        }
    }

    suspend fun setThreshold(minutes: Int) {
        context.dataStore.edit { it[Keys.THRESHOLD] = minutes.coerceIn(1, 15) }
    }

    /** Дамп расписания считается импортированным, если совпадает дата его генерации. */
    suspend fun scheduleImportedFor(generated: String): Boolean =
        context.dataStore.data.first()[Keys.SCHEDULE_IMPORTED] == generated

    suspend fun markScheduleImported(generated: String) {
        context.dataStore.edit { it[Keys.SCHEDULE_IMPORTED] = generated }
    }
}
