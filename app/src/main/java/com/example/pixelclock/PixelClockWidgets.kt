package com.example.pixelclock.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceComposable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import com.example.pixelclock.engine.ClockRefresher
import com.example.pixelclock.engine.ClockTickService
import com.example.pixelclock.render.ClockPalette
import com.example.pixelclock.render.ClockPalettes
import com.example.pixelclock.render.ClockRenderer
import com.example.pixelclock.render.ClockSettings
import com.example.pixelclock.render.ClockSettingsStore
import com.example.pixelclock.render.ClockTime
import com.example.pixelclock.ui.MainActivity
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Glance tree contains ONLY Box + Image (both RemoteViews-safe). All custom drawing happens offscreen
 * in ClockRenderer and is delivered as a Bitmap, so no unsupported Compose element can crash RemoteViews.
 */
abstract class BasePixelClockWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact // LocalSize == the real cell size in dp

    protected abstract fun draw(
        canvas: Canvas, w: Float, h: Float, time: ClockTime, palette: ClockPalette, settings: ClockSettings,
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { ClockBody() }
    }

    @Composable
    @GlanceComposable
    private fun ClockBody() {
        val context = LocalContext.current
        // Reading the tick pref subscribes this composition to ClockRefresher's per-minute state write,
        // which is what guarantees a recomposition (and therefore a fresh ClockTime.now()) on every tick.
        @Suppress("UNUSED_VARIABLE")
        val tick = currentState<Preferences>()[ClockRefresher.TICK_KEY]

        val time = ClockTime.now(context)
        val settings = ClockSettingsStore.load(context)
        val palette = ClockPalettes.resolve(context, settings.themeMode)
        val bitmap = renderBitmap(context, LocalSize.current, time, palette, settings)

        Box(
            modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity<MainActivity>()),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = "Clock ${time.singleLine}",
                modifier = GlanceModifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }

    private fun renderBitmap(
        context: Context, sizeDp: DpSize, time: ClockTime, palette: ClockPalette, settings: ClockSettings,
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        var w = sizeDp.width.value * density
        var h = sizeDp.height.value * density
        // RemoteViews travel over Binder; keep the bitmap small (≈1.2 MB max) to stay far below the limit.
        val cap = 560f / max(w, h)
        if (cap < 1f) { w *= cap; h *= cap }
        val bmp = Bitmap.createBitmap(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        draw(Canvas(bmp), bmp.width.toFloat(), bmp.height.toFloat(), time, palette, settings)
        return bmp
    }
}

class PixelScallopClockWidget : BasePixelClockWidget() {
    override fun draw(canvas: Canvas, w: Float, h: Float, time: ClockTime, palette: ClockPalette, settings: ClockSettings) =
        ClockRenderer.drawScallopClock(canvas, w, h, time, palette, settings.spec())
}

class PixelNumeralClockWidget : BasePixelClockWidget() {
    override fun draw(canvas: Canvas, w: Float, h: Float, time: ClockTime, palette: ClockPalette, settings: ClockSettings) =
        ClockRenderer.drawNumeralClock(canvas, w, h, time, palette)
}

class PixelScallopClockReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PixelScallopClockWidget()
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        ClockTickService.startSafely(context)
    }
}

class PixelNumeralClockReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PixelNumeralClockWidget()
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        ClockTickService.startSafely(context)
    }
}
