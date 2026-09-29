package com.example.pixelclock.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalContext
import com.example.pixelclock.render.ClockPalette
import com.example.pixelclock.render.ClockRenderer
import com.example.pixelclock.render.ClockTime
import com.example.pixelclock.render.ScallopSpec
import kotlinx.coroutines.delay

/** Pure Compose views for the in-app preview. Zero Glance / RemoteViews dependencies. */
@Composable
fun ScallopClockCanvas(time: ClockTime, palette: ClockPalette, spec: ScallopSpec, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawIntoCanvas { c -> ClockRenderer.drawScallopClock(c.nativeCanvas, size.width, size.height, time, palette, spec) }
    }
}

@Composable
fun NumeralClockCanvas(time: ClockTime, palette: ClockPalette, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawIntoCanvas { c -> ClockRenderer.drawNumeralClock(c.nativeCanvas, size.width, size.height, time, palette) }
    }
}

/** In-app time state; wakes exactly on each minute boundary (no polling). */
@Composable
fun rememberClockTime(): State<ClockTime> {
    val ctx = LocalContext.current
    return produceState(ClockTime.now(ctx)) {
        while (true) {
            value = ClockTime.now(ctx)
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
}
