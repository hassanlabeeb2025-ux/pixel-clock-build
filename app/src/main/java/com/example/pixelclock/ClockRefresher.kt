package com.example.pixelclock.engine

import android.content.Context
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.example.pixelclock.widget.PixelNumeralClockWidget
import com.example.pixelclock.widget.PixelScallopClockWidget

object ClockRefresher {
    val TICK_KEY = longPreferencesKey("tick")

    /**
     * Refreshes every placed instance of both widgets. Writes a new value into each widget's Glance state
     * first (guarantees recomposition), then calls update(). Equivalent to widget.updateAll(context),
     * but explicit about the state change. Returns how many widget instances exist (0 ⇒ service may stop).
     */
    suspend fun refreshAll(context: Context): Int {
        val app = context.applicationContext
        val manager = GlanceAppWidgetManager(app)
        val now = System.currentTimeMillis()
        var count = 0

        val scallop = PixelScallopClockWidget()
        for (id in manager.getGlanceIds(PixelScallopClockWidget::class.java)) {
            updateAppWidgetState(app, id) { it[TICK_KEY] = now }
            scallop.update(app, id); count++
        }
        val numeral = PixelNumeralClockWidget()
        for (id in manager.getGlanceIds(PixelNumeralClockWidget::class.java)) {
            updateAppWidgetState(app, id) { it[TICK_KEY] = now }
            numeral.update(app, id); count++
        }
        return count
    }
}
