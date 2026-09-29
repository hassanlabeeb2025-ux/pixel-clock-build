package com.example.pixelclock.render

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.text.format.DateFormat
import androidx.annotation.ColorInt
import androidx.annotation.RequiresApi
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import java.time.ZonedDateTime
import java.util.Locale

// ---------------------------------------------------------------- time

data class ClockTime(val hour24: Int, val minute: Int, val is24h: Boolean) {
    val hour12: Int get() = (hour24 % 12).let { if (it == 0) 12 else it }
    val isPm: Boolean get() = hour24 >= 12

    // Locale.US on purpose: guarantees Latin digits, which the bold system font renders reliably.
    val hourText: String get() = if (is24h) "%02d".format(Locale.US, hour24) else hour12.toString()
    val stackedHourText: String get() = "%02d".format(Locale.US, if (is24h) hour24 else hour12)
    val minuteText: String get() = "%02d".format(Locale.US, minute)
    val singleLine: String get() = "$hourText:$minuteText"

    companion object {
        fun now(context: Context): ClockTime {
            val n = ZonedDateTime.now() // system default zone (updated on ACTION_TIMEZONE_CHANGED)
            return ClockTime(n.hour, n.minute, DateFormat.is24HourFormat(context))
        }
    }
}

// ---------------------------------------------------------------- palette

enum class ClockThemeMode { LIGHT, DARK, DYNAMIC }

data class ClockPalette(
    @ColorInt val surface: Int,      // frame / dial background
    @ColorInt val onSurface: Int,    // text drawn directly on [surface]
    @ColorInt val container: Int,    // inner disc (scallop clock)
    @ColorInt val onContainer: Int,  // text drawn on [container]
    @ColorInt val accent: Int,
    @ColorInt val onAccent: Int,
    @ColorInt val muted: Int,
)

object ClockPalettes {
    val Light = ClockPalette(
        surface = 0xFFE9DDFF.toInt(), onSurface = 0xFF21005D.toInt(),
        container = 0xFFFFFBFE.toInt(), onContainer = 0xFF1D1B20.toInt(),
        accent = 0xFF6750A4.toInt(), onAccent = 0xFFFFFFFF.toInt(), muted = 0x7321005D,
    )
    val Dark = ClockPalette(
        surface = 0xFF4F378B.toInt(), onSurface = 0xFFEADDFF.toInt(),
        container = 0xFF141218.toInt(), onContainer = 0xFFE6E0E9.toInt(),
        accent = 0xFFD0BCFF.toInt(), onAccent = 0xFF381E72.toInt(), muted = 0x73EADDFF,
    )

    fun isNight(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    fun resolve(context: Context, mode: ClockThemeMode): ClockPalette = when (mode) {
        ClockThemeMode.LIGHT -> Light
        ClockThemeMode.DARK -> Dark
        ClockThemeMode.DYNAMIC ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamic(context)
            else if (isNight(context)) Dark else Light
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun dynamic(context: Context): ClockPalette {
        val s = if (isNight(context)) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        return ClockPalette(
            surface = s.primaryContainer.toArgb(),
            onSurface = s.onPrimaryContainer.toArgb(),
            container = s.surface.toArgb(),
            onContainer = s.onSurface.toArgb(),
            accent = s.primary.toArgb(),
            onAccent = s.onPrimary.toArgb(),
            muted = s.onPrimaryContainer.copy(alpha = 0.45f).toArgb(),
        )
    }
}

// ---------------------------------------------------------------- settings

data class ClockSettings(
    val themeMode: ClockThemeMode = ClockThemeMode.DYNAMIC,
    val lobes: Int = 12,
) {
    fun spec(): ScallopSpec = ScallopSpec(lobes = lobes.coerceIn(6, 24))
}

object ClockSettingsStore {
    private const val FILE = "pixel_clock_settings"

    fun load(context: Context): ClockSettings {
        val p = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val mode = runCatching { ClockThemeMode.valueOf(p.getString("theme", null) ?: "DYNAMIC") }
            .getOrDefault(ClockThemeMode.DYNAMIC)
        return ClockSettings(mode, p.getInt("lobes", 12))
    }

    fun save(context: Context, s: ClockSettings) {
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("theme", s.themeMode.name).putInt("lobes", s.lobes).apply()
    }
}
