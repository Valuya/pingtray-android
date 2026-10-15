package be.valuya.pingtray

import androidx.compose.ui.graphics.Color

/**
 * Same scheme as pingtray on the desktop: connection quality is one green to
 * red ramp, used everywhere quality is shown; destinations get hues off it.
 */
object Palette {
    val GOOD = Color(0xFF2EC27E)
    val DEGRADED = Color(0xFFF6D32D)
    val POOR = Color(0xFFFF7800)
    val BAD = Color(0xFFE01B24)
    val OUTAGE = Color(0xFF8B101A)
    private val RAMP = listOf(GOOD, DEGRADED, POOR, BAD, OUTAGE)

    val TARGETS = listOf(
        Color(0xFF62A0EA), Color(0xFFDC8ADD), Color(0xFF33C7DE),
        Color(0xFFB5A3FF), Color(0xFFDEDDDA), Color(0xFF1C71D8),
    )

    val BACKGROUND = Color(0xFF1D1D1D)
    val PANEL = Color(0xFF0B0B0B)
    val TEXT = Color(0xFFF6F5F4)
    val WEAK = Color(0xFF9A9996)

    fun lossLevel(loss: Double): Int = when {
        loss <= 0.0 -> 0
        loss < 0.05 -> 1
        loss < 0.20 -> 2
        loss < 1.0 -> 3
        else -> 4
    }

    fun latencyLevel(ms: Double, warn: Double, bad: Double): Int = when {
        ms >= bad -> 2
        ms >= warn -> 1
        else -> 0
    }

    fun loss(loss: Double) = RAMP[lossLevel(loss)]

    fun quality(loss: Double, medianMs: Double?, warn: Double, bad: Double) =
        RAMP[maxOf(lossLevel(loss), medianMs?.let { latencyLevel(it, warn, bad) } ?: 0)]
}
