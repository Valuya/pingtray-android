package be.valuya.pingtray

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow

/** One drawn series: a destination's ping or HTTP times, bucketed. */
class Series(val target: Target, val http: Boolean, val color: Color, val buckets: List<Bucket>) {
    val key get() = "${target.name}|${if (http) "http" else "ping"}"
    val label get() = "${target.name} ${if (http) "HTTP" else "ping"}"
}

class GraphData(
    val series: List<Series>,
    val bucketMs: Long,
    val notes: List<Note>,
    val fixes: List<Fix>,
    val phases: Map<String, List<Pair<Long, Phases>>>,
)

private val SPANS = listOf("5 min" to 300L, "15 min" to 900L, "1 h" to 3600L, "6 h" to 21600L, "24 h" to 86400L, "7 d" to 604800L)

@Composable
fun GraphScreen() {
    val context = LocalContext.current
    val settings = context.app.settings
    val db = context.app.db
    var spanSecs by remember { mutableLongStateOf(900L) }
    var end by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var live by remember { mutableStateOf(true) }
    var reference by remember { mutableStateOf(settings.reference) }
    var inspect by remember { mutableStateOf<Long?>(null) }
    var data by remember { mutableStateOf<GraphData?>(null) }

    LaunchedEffect(spanSecs, end, live) {
        while (true) {
            if (live) end = System.currentTimeMillis()
            val to = end
            val from = to - spanSecs * 1000
            data = withContext(Dispatchers.IO) { load(context, from, to, spanSecs) }
            if (!live) break
            delay(3_000)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SPANS.forEach { (label, secs) ->
                FilterChip(selected = live && spanSecs == secs, onClick = { spanSecs = secs; live = true; inspect = null }, label = { Text(label) })
            }
            FilterChip(selected = live, onClick = { live = !live }, label = { Text("Live") })
        }
        val d = data
        if (d == null) {
            Text("Loading…", Modifier.padding(16.dp), color = Palette.WEAK)
            return@Column
        }
        val ref = d.series.firstOrNull { it.key == reference } ?: d.series.firstOrNull { !it.http } ?: d.series.firstOrNull()
        Header(d, ref, end - spanSecs * 1000, end)
        Box(Modifier.fillMaxSize().padding(4.dp)) {
            var layout by remember { mutableStateOf<Layout?>(null) }
            Canvas(
                Modifier.fillMaxSize()
                    .pointerInput(spanSecs) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val l = layout ?: return@detectTransformGestures
                            if (zoom != 1f) spanSecs = (spanSecs / zoom).toLong().coerceIn(60, 30L * 86400)
                            if (pan.x != 0f) {
                                live = false
                                end -= (pan.x / l.plotW * spanSecs * 1000).toLong()
                            }
                        }
                    }
                    .pointerInput(d) {
                        detectTapGestures { pos ->
                            val l = layout ?: return@detectTapGestures
                            val ts = l.ts(pos.x)
                            inspect = ts
                            // tapping a curve makes it the reference
                            d.series.mapNotNull { s ->
                                val b = s.buckets.firstOrNull { ts >= it.ts && ts < it.ts + d.bucketMs } ?: return@mapNotNull null
                                val m = b.median ?: return@mapNotNull null
                                s to abs(l.y(m, s.http) - pos.y)
                            }.filter { it.second < 60f }.minByOrNull { it.second }?.let { (s, _) ->
                                reference = s.key
                                settings.reference = s.key
                            }
                        }
                    }
            ) {
                val l = Layout(size, end - spanSecs * 1000, end, d, settings)
                layout = l
                drawChart(l, d, ref)
                inspect?.let { ts ->
                    val x = l.x(ts)
                    drawLine(Color.White.copy(alpha = 0.6f), Offset(x, l.top), Offset(x, l.bottom), 1.5f)
                }
            }
            inspect?.let { ts -> Tooltip(d, ts, ref, Modifier.align(Alignment.TopEnd)) { inspect = null } }
        }
    }
}

private fun load(context: android.content.Context, from: Long, to: Long, spanSecs: Long): GraphData {
    val s = context.app.settings
    val db = context.app.db
    val pingBucket = bucketSeconds(spanSecs.toDouble(), s.intervalSecs.toDouble()) * 1000
    val series = s.targets.flatMapIndexed { i, t ->
        val color = Palette.TARGETS[i % Palette.TARGETS.size]
        buildList {
            add(Series(t, false, color, bucketize(db.samples(t.name, "ping", from - pingBucket, to), pingBucket)))
            if (t.http != null) add(Series(t, true, color, bucketize(db.samples(t.name, "http", from - pingBucket, to), pingBucket)))
        }
    }
    val phases = s.targets.filter { it.http != null }.associate { it.name to db.phases(it.name, from, to) }
    return GraphData(series, pingBucket, db.notes(from, to), db.fixes(from - 600_000, to + 600_000), phases)
}

/** Ping on the left axis, HTTP on its own scale on the right. */
private class Layout(size: Size, val from: Long, val to: Long, d: GraphData, s: Settings) {
    val left = 110f
    val right = size.width - 110f
    val top = 30f
    val bottom = size.height - 50f
    val plotW = right - left
    val plotH = bottom - top
    val pingTop = niceTop(d.series.filter { !it.http }.flatMap { s -> s.buckets.mapNotNull { it.median } })
    val httpTop = niceTop(d.series.filter { it.http }.flatMap { s -> s.buckets.mapNotNull { it.median } })
    val hasHttp = d.series.any { it.http && it.buckets.isNotEmpty() }
    val warn = s.warnMs.toDouble()

    fun x(ts: Long) = left + (ts - from).toFloat() / (to - from) * plotW
    fun ts(x: Float) = from + ((x - left) / plotW * (to - from)).toLong()
    fun y(ms: Double, http: Boolean) = bottom - ((ms / if (http) httpTop else pingTop).coerceIn(0.0, 1.05) * plotH).toFloat()
}

private fun alphaAt(q: Double, max: Float) = max * (1 - abs(2 * q - 1)).pow(0.8).toFloat()

private fun DrawScope.drawChart(l: Layout, d: GraphData, ref: Series?) {
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 26f; color = Palette.WEAK.toArgb() }
    drawRect(Palette.PANEL, Offset(l.left, l.top), Size(l.plotW, l.plotH))
    // value grid, both scales
    for (i in 0..4) {
        val f = i / 4.0
        val y = l.bottom - (f * l.plotH).toFloat()
        drawLine(Color.White.copy(alpha = 0.08f), Offset(l.left, y), Offset(l.right, y))
        drawContext.canvas.nativeCanvas.drawText("%.0f".format(l.pingTop * f), 8f, y + 9f, text)
        if (l.hasHttp) drawContext.canvas.nativeCanvas.drawText("%.0f".format(l.httpTop * f), l.right + 8f, y + 9f, text)
    }
    val title = Paint(text).apply { color = Palette.TEXT.toArgb(); isFakeBoldText = true }
    drawContext.canvas.nativeCanvas.drawText("ping ms", 8f, l.top - 6f, title)
    if (l.hasHttp) drawContext.canvas.nativeCanvas.drawText("HTTP ms", l.right - 10f, l.top - 6f, title)
    // time grid
    val span = (l.to - l.from) / 1000
    val step = STEPS.firstOrNull { span / it <= 4 } ?: STEPS.last()
    val fmt = SimpleDateFormat(if (step >= 86400) "EEE d" else "HH:mm", Locale.getDefault())
    val offset = java.util.TimeZone.getDefault().getOffset(l.from)
    var t = ((l.from + offset) / (step * 1000) + 1) * step * 1000 - offset
    while (t < l.to) {
        val x = l.x(t)
        drawLine(Color.White.copy(alpha = 0.08f), Offset(x, l.top), Offset(x, l.bottom))
        drawContext.canvas.nativeCanvas.drawText(fmt.format(Date(t)), x - 30f, l.bottom + 36f, text)
        t += step * 1000
    }

    // the reference: smoke (a vertical gradient per time slot), total losses
    ref?.let { s ->
        val maxAlpha = if (s.http) 0.32f else 0.5f
        for (b in s.buckets) {
            val x0 = l.x(b.ts).coerceAtLeast(l.left)
            val x1 = l.x(b.ts + d.bucketMs).coerceAtMost(l.right)
            if (x1 <= x0) continue
            val p = b.pcts
            if (p == null) {
                drawRect(Palette.OUTAGE.copy(alpha = 0.3f), Offset(x0, l.top), Size(x1 - x0, l.plotH))
                continue
            }
            val yTop = l.y(p[20], s.http)
            val yBottom = l.y(p[0], s.http)
            if (yBottom - yTop < 1f) continue
            val stops = (20 downTo 0).map { k ->
                ((l.y(p[k], s.http) - yTop) / (yBottom - yTop)).coerceIn(0f, 1f) to s.color.copy(alpha = alphaAt(PCTS[k], maxAlpha))
            }.toTypedArray()
            drawRect(Brush.verticalGradient(*stops, startY = yTop, endY = yBottom), Offset(x0, yTop), Size(x1 - x0, yBottom - yTop))
        }
    }

    // medians as steps: the others faint, the reference last and in full
    val ordered = d.series.filter { it !== ref } + listOfNotNull(ref)
    for (s in ordered) {
        val isRef = s === ref
        val path = Path()
        var prevEnd: Long? = null
        for (b in s.buckets) {
            val m = b.median ?: run { prevEnd = null; null } ?: continue
            val y = l.y(m, s.http)
            val x0 = l.x(b.ts)
            val x1 = l.x(b.ts + d.bucketMs)
            if (prevEnd == b.ts) path.lineTo(x0, y) else path.moveTo(x0, y)
            path.lineTo(x1, y)
            prevEnd = b.ts + d.bucketMs
        }
        drawPath(
            path, s.color.copy(alpha = if (isRef) 1f else 0.55f),
            style = Stroke(width = if (isRef) 5f else 2.5f, pathEffect = if (s.http) PathEffect.dashPathEffect(floatArrayOf(14f, 8f)) else null),
        )
    }

    // loss marks for the reference along the bottom
    ref?.buckets?.filter { it.lost > 0 }?.forEach { b ->
        val x0 = l.x(b.ts).coerceAtLeast(l.left)
        val x1 = l.x(b.ts + d.bucketMs).coerceAtMost(l.right)
        if (x1 > x0) drawRect(Palette.loss(b.loss), Offset(x0, l.bottom - 12f), Size(x1 - x0, 12f))
    }

    // notes
    val noteText = Paint(text).apply { color = Palette.TEXT.toArgb() }
    for (n in d.notes) {
        val x = l.x(n.ts)
        drawLine(Color.White.copy(alpha = 0.5f), Offset(x, l.top), Offset(x, l.bottom), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
        drawContext.canvas.nativeCanvas.drawText(n.text, x + 6f, l.top + 28f, noteText)
    }
}

@Composable
private fun Header(d: GraphData, ref: Series?, from: Long, to: Long) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        for (s in d.series) {
            val visible = s.buckets.filter { it.ts in from..to }
            val meds = visible.mapNotNull { it.median }.sorted()
            val count = visible.sumOf { it.count }
            val lost = visible.sumOf { it.lost }
            val loss = if (count == 0) 0.0 else lost.toDouble() / count
            Text(
                buildString {
                    append(s.label)
                    if (meds.isNotEmpty()) append(" ").append(fmtMs(percentile(meds, 0.5)))
                    if (lost > 0) append(" · %.1f %%".format(loss * 100))
                },
                color = s.color,
                fontSize = 13.sp,
                fontWeight = if (s === ref) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

/** What happened at the tapped moment: every series, where HTTP time went, where you were. */
@Composable
private fun Tooltip(d: GraphData, ts: Long, ref: Series?, modifier: Modifier, onClose: () -> Unit) {
    val fmt = SimpleDateFormat("EEE d/MM HH:mm:ss", Locale.getDefault())
    Column(
        modifier.padding(8.dp).background(Color(0xEE222226), RoundedCornerShape(8.dp)).padding(10.dp)
            .pointerInput(Unit) { detectTapGestures { onClose() } }
    ) {
        Text(fmt.format(Date(ts)), color = Palette.TEXT, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        for (s in d.series) {
            val b = s.buckets.firstOrNull { ts >= it.ts && ts < it.ts + d.bucketMs }
            val p = b?.pcts
            val line = when {
                b == null -> "no data"
                p == null -> "all lost (${b.count})"
                else -> "${fmtMs(p[MEDIAN])}  (${fmtMs(p[P10])} – ${fmtMs(p[P90])})  lost ${b.lost}/${b.count}"
            }
            Text(
                "${s.label}: $line", color = s.color, fontSize = 12.sp,
                fontWeight = if (s === ref) FontWeight.Bold else FontWeight.Normal,
            )
            if (s.http && s === ref) {
                d.phases[s.target.name]?.filter { it.first in (b?.ts ?: 0)..((b?.ts ?: 0) + d.bucketMs) }?.lastOrNull()?.let { (_, ph) ->
                    Text(
                        "   DNS ${fmtMs(ph.dns)} · connect ${fmtMs(ph.connect)} · TLS ${fmtMs(ph.tls)} · answer ${fmtMs(ph.wait)}",
                        color = Palette.WEAK, fontSize = 11.sp,
                    )
                }
            }
        }
        d.fixes.minByOrNull { abs(it.ts - ts) }?.takeIf { abs(it.ts - ts) < 120_000 }?.let { f ->
            Text("at %.5f, %.5f (±%.0f m)".format(f.lat, f.lon, f.acc ?: 30f), color = Palette.WEAK, fontSize = 11.sp)
        }
        d.notes.lastOrNull { it.ts <= ts && ts - it.ts < 4 * 3_600_000 }?.let { n ->
            Text("Note: ${n.text}", color = Palette.TEXT, fontSize = 11.sp)
        }
    }
}
