package com.malinovka.widget

import android.content.Context
import androidx.room.Room
import com.malinovka.widget.data.ArrivalsRepository
import com.malinovka.widget.data.BtransArrivalsSource
import com.malinovka.widget.data.ScheduleDumpRepository
import com.malinovka.widget.data.SettingsRepository
import com.malinovka.widget.data.YandexArrivalsSource
import com.malinovka.widget.data.db.AppDatabase
import com.malinovka.widget.util.ErrorLog
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Простой сервис-локатор: приложение крошечное, DI-фреймворк не нужен.
 * Инициализируется один раз из Application.onCreate.
 */
object AppGraph {

    lateinit var appContext: Context
        private set

    val json: Json by lazy {
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }

    private val baseHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()
    }

    /** Яндекс-страница тяжёлая, даём ей чуть больше времени. */
    val yandexClient: OkHttpClient by lazy {
        baseHttpClient.newBuilder()
            .callTimeout(7, TimeUnit.SECONDS)
            .build()
    }

    val btransClient: OkHttpClient by lazy {
        baseHttpClient.newBuilder()
            .callTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    val db: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, "malinovka.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }

    val dumpRepository: ScheduleDumpRepository by lazy {
        ScheduleDumpRepository(appContext, db.scheduleDao(), settings, json)
    }

    val repository: ArrivalsRepository by lazy {
        ArrivalsRepository(
            yandex = YandexArrivalsSource(yandexClient),
            btrans = BtransArrivalsSource(btransClient, json),
            dump = dumpRepository,
            snapshotDao = db.snapshotDao(),
            scheduleDao = db.scheduleDao(),
            json = json,
        )
    }

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        ErrorLog.init(appContext)
    }
}
