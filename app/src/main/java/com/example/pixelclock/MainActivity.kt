package com.example.pixelclock.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.pixelclock.engine.ClockRefresher
import com.example.pixelclock.engine.ClockTickService
import com.example.pixelclock.render.ClockPalettes
import com.example.pixelclock.render.ClockSettings
import com.example.pixelclock.render.ClockSettingsStore
import com.example.pixelclock.render.ClockThemeMode
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ClockTickService.startSafely(this) // app is visible ⇒ FGS start is always allowed here
        setContent { MaterialTheme { PreviewScreen() } }
    }
}

@Composable
fun PreviewScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(ClockSettingsStore.load(ctx)) }
    val time by rememberClockTime()
    val palette = remember(settings.themeMode) { ClockPalettes.resolve(ctx, settings.themeMode) }

    if (Build.VERSION.SDK_INT >= 33) {
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
        LaunchedEffect(Unit) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }

    fun commit(new: ClockSettings) {
        settings = new
        ClockSettingsStore.save(ctx, new)
        scope.launch { ClockRefresher.refreshAll(ctx) } // push to already-placed widgets immediately
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Pixel Clock Widgets", style = MaterialTheme.typography.headlineSmall)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ClockThemeMode.entries.forEach { m ->
                FilterChip(
                    selected = settings.themeMode == m,
                    onClick = { commit(settings.copy(themeMode = m)) },
                    label = { Text(m.name) },
                )
            }
        }

        Text("Scallop lobes: ${settings.lobes}")
        Slider(
            value = settings.lobes.toFloat(),
            onValueChange = { settings = settings.copy(lobes = it.roundToInt()) },
            onValueChangeFinished = { commit(settings) },
            valueRange = 6f..24f,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ScallopClockCanvas(time, palette, settings.spec(), Modifier.size(160.dp))
            NumeralClockCanvas(time, palette, Modifier.size(160.dp))
        }
    }
}
