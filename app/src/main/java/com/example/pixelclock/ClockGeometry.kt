package com.example.pixelclock.render

import android.graphics.Path
import android.graphics.PointF
import kotlin.math.cos
import kotlin.math.sin

/**
 * Scalloped frame description.
 * The outline is the polar curve  r(θ) = r0 · (1 + depth · cos(lobes · (θ − phase))).
 */
data class ScallopSpec(
    val lobes: Int = 12,
    /** Relative amplitude a. Peak radius = r0(1+a), valley radius = r0(1−a). */
    val depth: Float = 0.07f,
    /** Cubic segments per lobe. 4 keeps the Hermite→Bézier error far below 1 px. */
    val segmentsPerLobe: Int = 4,
    /** Clockwise rotation in degrees. 0 = a lobe peak sits exactly at 12 o'clock. */
    val rotationDeg: Double = 0.0,
) {
    init {
        require(lobes >= 3) { "lobes must be >= 3" }
        require(depth in 0f..0.5f) { "depth must be in 0..0.5" }
        require(segmentsPerLobe >= 2) { "segmentsPerLobe must be >= 2" }
    }
}

object ClockGeometry {

    /**
     * Angle (radians) of slot [index] out of [count] slots, clockwise on screen (y grows downward).
     * offsetDeg = -90 puts slot 0 (and slot [count] == 12 on a dial) at the exact top.
     */
    fun angleRad(index: Int, count: Int, offsetDeg: Double = -90.0): Double =
        Math.toRadians(index * 360.0 / count + offsetDeg)

    fun polar(cx: Float, cy: Float, radius: Float, theta: Double): PointF =
        PointF(cx + radius * cos(theta).toFloat(), cy + radius * sin(theta).toFloat())

    /** r0 such that the lobe PEAK touches [outerRadius] (so the frame never clips the canvas). */
    fun scallopBaseRadius(outerRadius: Float, depth: Float): Float = outerRadius / (1f + depth)

    /** Radius of the valleys – the largest circle that fits fully inside the frame. */
    fun scallopValleyRadius(outerRadius: Float, depth: Float): Float =
        scallopBaseRadius(outerRadius, depth) * (1f - depth)

    /**
     * Builds the closed scalloped outline with exact analytic tangents.
     *
     *   x(θ) = cx + r(θ)cosθ            y(θ) = cy + r(θ)sinθ
     *   r'(θ) = −r0·a·n·sin(n(θ−φ))
     *   x'(θ) = r'cosθ − r sinθ         y'(θ) = r'sinθ + r cosθ
     *
     * Each θ-interval [t0, t1] (Δ = t1−t0) becomes one cubic Bézier via the Hermite→Bézier identity:
     *   C1 = P(t0) + P'(t0)·Δ/3       C2 = P(t1) − P'(t1)·Δ/3
     * Tangent-continuous at every joint, scales linearly with [outerRadius].
     * Returns an android.graphics.Path (use `.asComposePath()` if you need a Compose Path).
     */
    fun buildScallopPath(cx: Float, cy: Float, outerRadius: Float, spec: ScallopSpec): Path {
        val n = spec.lobes
        val a = spec.depth.toDouble()
        val r0 = scallopBaseRadius(outerRadius, spec.depth).toDouble()
        val phase = Math.toRadians(spec.rotationDeg - 90.0)
        val cxD = cx.toDouble()
        val cyD = cy.toDouble()

        fun r(t: Double) = r0 * (1.0 + a * cos(n * (t - phase)))
        fun dr(t: Double) = -r0 * a * n * sin(n * (t - phase))
        fun px(t: Double) = cxD + r(t) * cos(t)
        fun py(t: Double) = cyD + r(t) * sin(t)
        fun dx(t: Double) = dr(t) * cos(t) - r(t) * sin(t)
        fun dy(t: Double) = dr(t) * sin(t) + r(t) * cos(t)

        val segments = n * spec.segmentsPerLobe
        val dT = 2.0 * Math.PI / segments
        val path = Path()
        var t0 = phase
        path.moveTo(px(t0).toFloat(), py(t0).toFloat())
        repeat(segments) {
            val t1 = t0 + dT
            path.cubicTo(
                (px(t0) + dx(t0) * dT / 3.0).toFloat(), (py(t0) + dy(t0) * dT / 3.0).toFloat(),
                (px(t1) - dx(t1) * dT / 3.0).toFloat(), (py(t1) - dy(t1) * dT / 3.0).toFloat(),
                px(t1).toFloat(), py(t1).toFloat(),
            )
            t0 = t1
        }
        path.close()
        return path
    }
}
