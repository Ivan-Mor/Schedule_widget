package com.malinovka.widget.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.ui.res.stringResource
import com.malinovka.widget.AppGraph
import com.malinovka.widget.R
import com.malinovka.widget.data.SettingsState
import com.malinovka.widget.data.Stops
import com.malinovka.widget.receivers.ThemeAlarm
import com.malinovka.widget.widget.ScheduleWidget
import com.malinovka.widget.widget.WidgetRefresher
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeAlarm.ensureNext(this)
        WidgetRefresher.request(this, force = false)
        setContent {
            AppTheme {
                SettingsScreen()
            }
        }
    }
}

@Composable
private fun AppTheme(content: @Composable () -> Unit) {
    // Та же граница, что у виджета: тёмная тема 18:00–06:00.
    MaterialTheme(
        colorScheme = if (ScheduleWidget.isDarkNow()) darkColorScheme() else lightColorScheme(),
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(SettingsState()) }

    LaunchedEffect(Unit) {
        AppGraph.settings.state.collect { state = it }
    }

    var notificationsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notificationsGranted = granted }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionTitle(stringResource(R.string.settings_direction_title))
            Stops.ALL.forEach { cfg ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    RadioButton(
                        selected = state.leavingStopKey == cfg.key,
                        onClick = { scope.launch { AppGraph.settings.setLeavingStop(cfg.key) } },
                    )
                    Text(cfg.title)
                }
            }

            SectionTitle(stringResource(R.string.settings_routes_title))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stops.DEFAULT_ROUTES.forEach { route ->
                    FilterChip(
                        selected = route in state.routes,
                        onClick = { scope.launch { AppGraph.settings.toggleRoute(route) } },
                        label = { Text(route) },
                    )
                }
            }

            SectionTitle(
                stringResource(R.string.settings_threshold_title) + ": ${state.thresholdMinutes}"
            )
            Slider(
                value = state.thresholdMinutes.toFloat(),
                onValueChange = { scope.launch { AppGraph.settings.setThreshold(it.toInt()) } },
                valueRange = 1f..15f,
                steps = 13,
            )

            if (!notificationsGranted) {
                Button(onClick = {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) {
                    Text(stringResource(R.string.settings_notifications))
                }
            } else {
                Text(
                    "Уведомления разрешены ✓",
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Button(onClick = {
                scope.launch {
                    WidgetRefresher.refresh(context, force = true)
                    Toast.makeText(context, "Виджет обновлён", Toast.LENGTH_SHORT).show()
                }
            }) {
                Text(stringResource(R.string.settings_update_now))
            }

            Text(
                stringResource(R.string.settings_battery_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Text(
                "Виджет добавляется долгим нажатием по рабочему столу → «Виджеты».",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}
