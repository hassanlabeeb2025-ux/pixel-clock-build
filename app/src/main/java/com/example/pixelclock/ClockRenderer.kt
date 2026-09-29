package com.example.pixelclock.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import kotlin.math.hypot
import kotlin.math.min

/**
 * Single source of truth for pixels. It draws on an android.graphics.Canvas, so the exact same code path feeds:
 *  - the in-app Compose preview (via drawIntoCanvas { it.nativeCanvas })
 *  - the Glance widget (via an offscreen Bitmap → ImageProvider)
 * Preview and launcher output therefore cannot drift apart.
 */
object ClockRenderer {

    private const val ACTIVE_SCALE = 1.28f

    val defaultTypeface: Typeface by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(Typeface.DEFAULT, 800, false)
        else Typeface.DEFAULT_BOLD
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; this.color = color }

    private fun text(color: Int, size: Float, tf: Typeface) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; textSize = size; typeface = tf; textAlign = Paint.Align.CENTER
    }

    /** Centers the INK of [s] on (cx, cy) – ignores font ascent/descent padding entirely. */
    private fun Canvas.drawInkCentered(s: String, cx: Float, cy: Float, paint: Paint) {
        val b = Rect()
        paint.getTextBounds(s, 0, s.length, b)
        drawText(s, cx, cy - b.exactCenterY(), paint)
    }

    // ------------------------------------------------------------ 1) Scallop clock

    fun drawScallopClock(
        canvas: Canvas, width: Float, height: Float,
        time: ClockTime, palette: ClockPalette, spec: ScallopSpec,
        typeface: Typeface = defaultTypeface,
    ) {
        val cx = width / 2f
        val cy = height / 2f
        val outer = min(width, height) / 2f - 1f  // 1px safety so AA never clips

        canvas.drawPath(ClockGeometry.buildScallopPath(cx, cy, outer, spec), fill(palette.surface))

        val valley = ClockGeometry.scallopValleyRadius(outer, spec.depth)
        val disc = valley * 0.80f
        canvas.drawCircle(cx, cy, disc, fill(palette.container))

        val s = time.singleLine
        val p = text(palette.onContainer, 100f, typeface)
        p.textSize = min(100f * (disc * 1.55f) / p.measureText(s), disc * 0.9f) // fit chord width
        val dy = if (time.is24h) 0f else -disc * 0.10f
        canvas.drawInkCentered(s, cx, cy + dy, p)

        if (!time.is24h) {
            val ap = text(palette.accent, disc * 0.24f, typeface)
            canvas.drawInkCentered(if (time.isPm) "PM" else "AM", cx, cy + disc * 0.44f, ap)
        }
    }

    // ------------------------------------------------------------ 2) Numeral clock

    fun drawNumeralClock(
        canvas: Canvas, width: Float, height: Float,
        time: ClockTime, palette: ClockPalette,
        typeface: Typeface = defaultTypeface,
    ) {
        val cx = width / 2f
        val cy = height / 2f
        val outer = min(width, height) / 2f - 1f
        canvas.drawCircle(cx, cy, outer, fill(palette.surface))

        // Radial budget (outside → inside): minute track | gap | numerals | gap | center digits
        val dotR = outer * 0.035f
        val trackR = outer - dotR - outer * 0.03f
        val numeralLimit = trackR - dotR - outer * 0.03f

        // Worst-case numeral footprint: widest string "12" at the ACTIVE (largest) scale.
        val numeralSize = outer * 0.17f
        val probe = text(0, numeralSize * ACTIVE_SCALE, typeface)
        val pb = Rect().also { probe.getTextBounds("12", 0, 2, it) }
        val extent = hypot(pb.width() / 2f, pb.height() / 2f) * 1.1f  // radius of a numeral's footprint
        val numeralR = numeralLimit - extent   // ⇒ outer edge of ANY numeral ≤ numeralLimit, never clips

        // Minute track + orbiting minute dot (0..59 → angle − 90°)
        val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = outer * 0.008f; color = palette.muted
        }
        canvas.drawCircle(cx, cy, trackR, track)
        val m = ClockGeometry.polar(cx, cy, trackR, ClockGeometry.angleRad(time.minute, 60))
        canvas.drawCircle(m.x, m.y, dotR, fill(palette.accent))

        // Numerals 1..12 — i=12 → −90° → exactly at the top.
        val activeHour = time.hour12
        for (i in 1..12) {
            val pos = ClockGeometry.polar(cx, cy, numeralR, ClockGeometry.angleRad(i, 12))
            if (i == activeHour) {
                canvas.drawCircle(pos.x, pos.y, extent, fill(palette.accent))
                canvas.drawInkCentered(i.toString(), pos.x, pos.y, text(palette.onAccent, numeralSize * ACTIVE_SCALE, typeface))
            } else {
                canvas.drawInkCentered(i.toString(), pos.x, pos.y, text(palette.muted.opaque(), numeralSize, typeface))
            }
        }

        // Center stacked HH / MM, shrunk until its bounding corner fits inside the free disc.
        val innerR = numeralR - extent - outer * 0.03f
        val hp = text(palette.onSurface, 10f, typeface)
        val bb = Rect()
        var size = innerR * 1.1f
        while (size > 4f) {
            hp.textSize = size
            hp.getTextBounds("88", 0, 2, bb)
            if (hypot(bb.width() / 2f, size * 0.8f) <= innerR) break
            size *= 0.95f
        }
        val mp = text(palette.accent, size, typeface)
        hp.textSize = size
        canvas.drawInkCentered(time.stackedHourText, cx, cy - size * 0.42f, hp)
        canvas.drawInkCentered(time.minuteText, cx, cy + size * 0.42f, mp)
    }

    /** Numerals use a dimmed-but-readable version of the muted tone. */
    private fun Int.opaque(): Int = (this and 0x00FFFFFF) or (0xB0 shl 24)
}
