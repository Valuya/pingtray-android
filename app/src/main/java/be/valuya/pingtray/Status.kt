package be.valuya.pingtray

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.graphics.toArgb

/** What the notification shows: the reference destination, now and over 5 minutes. */
class Status(val title: String, val text: String, val graph: Bitmap?, val signature: String) {
    companion object {
        private const val COLUMNS = 20
        private const val COLUMN_MS = 15_000L

        fun compute(context: Context): Status {
            val s = context.app.settings
            val db = context.app.db
            val targets = s.targets
            val ref = s.reference?.substringBefore('|')?.let { n -> targets.firstOrNull { it.name == n } } ?: targets.firstOrNull()
                ?: return Status("pingtray", "no destination", null, "none")
            val now = System.currentTimeMillis()
            val end = (now / COLUMN_MS + 1) * COLUMN_MS
            val start = end - COLUMNS * COLUMN_MS
            val samples = db.samples(ref.name, "ping", start, end)
            val buckets = bucketize(samples, COLUMN_MS).associateBy { it.ts }
            val columns = (0 until COLUMNS).map { buckets[start + it * COLUMN_MS] }
            val recent = samples.filter { it.first >= now - 6_000 }
            val replies = recent.mapNotNull { it.second }.sorted()
            val current = if (replies.isNotEmpty()) percentile(replies, 0.5) else null
            val lost5 = samples.count { it.second == null }
            val loss = if (samples.isEmpty()) 0.0 else lost5.toDouble() / samples.size
            val httpLast = db.samples(ref.name, "http", now - 30_000, now + 1).lastOrNull()
            val title = when {
                current != null -> "${ref.name}: ${fmtMs(current)}"
                recent.isNotEmpty() -> "${ref.name}: lost"
                else -> ref.name
            }
            val text = buildList {
                add("loss %.1f %% (5 min)".format(loss * 100))
                httpLast?.let { (_, ms) -> add(if (ms == null) "HTTP failed" else "HTTP ${fmtMs(ms)}") }
                if (s.laptopUrl.isNotBlank()) add("${db.unsentCount()} positions to send")
            }.joinToString(" · ")
            val graph = render(columns, s)
            return Status(title, text, graph, title + text + columns.joinToString { "${it?.median?.toInt()}/${it?.lost}" })
        }

        /** SmokePing-style mini graph: spread as grey smoke, median coloured by loss. */
        private fun render(columns: List<Bucket?>, s: Settings): Bitmap {
            val w = 400
            val h = 120
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = 0xFF1D1D1D.toInt()
            c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), 12f, 12f, paint)
            val medians = columns.mapNotNull { it?.median }
            val top = niceTop(medians)
            fun y(ms: Double) = (h - 6 - (ms / top).coerceIn(0.0, 1.0) * (h - 12)).toFloat()
            val cw = w.toFloat() / columns.size
            columns.forEachIndexed { i, b ->
                if (b == null) return@forEachIndexed
                val x0 = i * cw + 1
                val x1 = (i + 1) * cw - 1
                val p = b.pcts
                if (p == null) {
                    paint.color = Palette.OUTAGE.toArgb()
                    c.drawRect(x0, 0f, x1, h.toFloat(), paint)
                    return@forEachIndexed
                }
                paint.color = 0x40FFFFFF
                c.drawRect(x0, y(p[20]), x1, y(p[0]), paint)
                paint.color = 0x60FFFFFF
                c.drawRect(x0, y(p[15]), x1, y(p[5]), paint)
                paint.color = Palette.loss(b.loss).toArgb()
                c.drawRect(x0, y(p[MEDIAN]) - 3, x1, y(p[MEDIAN]) + 3, paint)
            }
            paint.color = 0x99FFFFFF.toInt()
            paint.textSize = 22f
            c.drawText("${top.toInt()} ms", 8f, 24f, paint)
            return bmp
        }
    }
}
