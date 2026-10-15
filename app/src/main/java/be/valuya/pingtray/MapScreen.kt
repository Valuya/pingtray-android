package be.valuya.pingtray

import android.graphics.DashPathEffect
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline

/** Fixes closer than this are joined by a solid line; farther apart, the route is unknown. */
private const val TRUSTED_GAP_MS = 90_000L

@Composable
fun MapScreen() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var hours by remember { mutableLongStateOf(24L) }
    var track by remember { mutableStateOf<List<Pair<Fix, androidx.compose.ui.graphics.Color>>>(emptyList()) }
    var fitted by remember { mutableStateOf(false) }
    val map = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(13.0)
        }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) map.onResume()
            if (e == Lifecycle.Event.ON_PAUSE) map.onPause()
        }
        lifecycle.addObserver(observer)
        map.onResume()
        onDispose { lifecycle.removeObserver(observer); map.onPause() }
    }
    LaunchedEffect(hours) {
        track = withContext(Dispatchers.IO) { colouredTrack(context, hours) }
        fitted = false
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("1 h" to 1L, "6 h" to 6L, "24 h" to 24L, "7 d" to 168L).forEach { (label, h) ->
                FilterChip(selected = hours == h, onClick = { hours = h }, label = { Text(label) })
            }
        }
        Text(
            if (track.isEmpty()) "No position in this range." else "${track.size} positions · colour = connection (green good → red bad) · dotted = route unknown",
            color = Palette.WEAK, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp),
        )
        AndroidView({ map }, Modifier.fillMaxSize()) { m ->
            m.overlays.clear()
            track.zipWithNext().forEach { (a, b) ->
                val (f0, c) = a
                val (f1, _) = b
                if (f1.ts - f0.ts > 600_000) return@forEach
                val line = Polyline(m).apply {
                    setPoints(listOf(GeoPoint(f0.lat, f0.lon), GeoPoint(f1.lat, f1.lon)))
                    outlinePaint.color = c.toArgb()
                    outlinePaint.strokeWidth = 12f
                    if (f1.ts - f0.ts > TRUSTED_GAP_MS) {
                        outlinePaint.pathEffect = DashPathEffect(floatArrayOf(6f, 18f), 0f)
                        outlinePaint.alpha = 140
                    }
                }
                m.overlays.add(line)
            }
            if (!fitted && track.size >= 2) {
                val box = BoundingBox.fromGeoPoints(track.map { GeoPoint(it.first.lat, it.first.lon) })
                m.post { m.zoomToBoundingBox(box.increaseByScale(1.3f), false) }
                fitted = true
            } else if (!fitted && track.size == 1) {
                m.controller.setCenter(GeoPoint(track[0].first.lat, track[0].first.lon))
                fitted = true
            }
            m.invalidate()
        }
    }
}

/** Each fix with the connection quality around it (reference destination's pings, per minute). */
private fun colouredTrack(context: android.content.Context, hours: Long): List<Pair<Fix, androidx.compose.ui.graphics.Color>> {
    val s = context.app.settings
    val db = context.app.db
    val to = System.currentTimeMillis()
    val from = to - hours * 3_600_000
    val fixes = db.fixes(from, to).filter { (it.acc ?: 30f) <= 5_000f }
    val ref = s.reference?.substringBefore('|')?.let { n -> s.targets.firstOrNull { it.name == n } } ?: s.targets.firstOrNull()
        ?: return fixes.map { it to Palette.WEAK }
    val buckets = bucketize(db.samples(ref.name, "ping", from, to), 60_000).associateBy { it.ts }
    return fixes.map { f ->
        val b = buckets[Math.floorDiv(f.ts, 60_000L) * 60_000]
        f to (if (b == null) Palette.WEAK else Palette.quality(b.loss, b.median, s.warnMs.toDouble(), s.badMs.toDouble()))
    }
}
