package be.valuya.pingtray

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.util.Base64
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * Measures in the background: pings and HTTP probes to each destination,
 * the phone's GPS, and sends positions to pingtray on the laptop. Shows a
 * permanent notification with the current latency and a mini graph.
 */
class ProbeService : LifecycleService() {
    private val locationListener = LocationListener { onLocation(it) }
    private var lastFixTs = 0L

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Connection status", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
        )
        startForeground(NOTIFICATION, notification(null), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        startLocation()
        val settings = app.settings
        for (t in settings.targets) {
            lifecycleScope.launch(Dispatchers.IO) { pingLoop(t) }
            if (t.http != null) lifecycleScope.launch(Dispatchers.IO) { httpLoop(t) }
        }
        lifecycleScope.launch(Dispatchers.IO) { uploadLoop() }
        lifecycleScope.launch(Dispatchers.IO) { notificationLoop() }
        lifecycleScope.launch(Dispatchers.IO) {
            app.db.purgeBefore(System.currentTimeMillis() - 180L * 86_400_000)
        }
    }

    override fun onDestroy() {
        getSystemService(LocationManager::class.java).removeUpdates(locationListener)
        super.onDestroy()
    }

    private suspend fun pingLoop(t: Target) {
        val s = app.settings
        val period = (s.intervalSecs * 1000).toLong()
        var addr: InetAddress? = null
        var resolvedAt = 0L
        while (lifecycleScope.isActive) {
            val tick = System.currentTimeMillis()
            if (addr == null || tick - resolvedAt > 300_000) {
                addr = runCatching { InetAddress.getByName(t.host) }.getOrNull()
                resolvedAt = tick
            }
            val rtt = addr?.let { Icmp.ping(it, minOf(s.timeoutMs, (period * 0.9).toInt())) }
            app.db.insertSample(t.name, "ping", tick, rtt)
            delay(maxOf(0, period - (System.currentTimeMillis() - tick)))
        }
    }

    private suspend fun httpLoop(t: Target) {
        val s = app.settings
        val period = (s.httpIntervalSecs * 1000).toLong()
        while (lifecycleScope.isActive) {
            val tick = System.currentTimeMillis()
            val result = HttpProbe.probe(t.http!!, minOf(s.httpTimeoutMs, (period * 0.9).toInt()))
            app.db.insertSample(t.name, "http", tick, result?.first?.total?.let { (it * 1000).toLong() })
            result?.let { (p, status) -> app.db.insertHttp(t.name, tick, p, status) }
            delay(maxOf(0, period - (System.currentTimeMillis() - tick)))
        }
    }

    private fun startLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val lm = getSystemService(LocationManager::class.java)
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            if (lm.isProviderEnabled(provider)) {
                lm.requestLocationUpdates(provider, 3_000L, 0f, locationListener, Looper.getMainLooper())
            }
        }
    }

    private fun onLocation(l: Location) {
        // one fix every 3 s at most; GPS wins over network when both report
        if (l.time - lastFixTs < 3_000) return
        if (l.provider == LocationManager.NETWORK_PROVIDER && System.currentTimeMillis() - lastFixTs < 15_000) return
        lastFixTs = l.time
        val fix = Fix(
            l.time, l.latitude, l.longitude,
            if (l.hasAccuracy()) l.accuracy else null,
            if (l.hasAltitude()) l.altitude else null,
            if (l.hasSpeed()) l.speed else null,
        )
        lifecycleScope.launch(Dispatchers.IO) { app.db.insertFix(fix) }
    }

    /** Positions to pingtray on the laptop, in OwnTracks' format; queued while unreachable. */
    private suspend fun uploadLoop() {
        val http = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
        while (lifecycleScope.isActive) {
            val s = app.settings
            if (s.laptopUrl.isNotBlank()) {
                val batch = app.db.unsentFixes(500)
                if (batch.isNotEmpty()) {
                    val body = JSONArray(batch.map { f ->
                        JSONObject().put("_type", "location").put("tst", f.ts / 1000).put("lat", f.lat).put("lon", f.lon).apply {
                            f.acc?.let { put("acc", it.toDouble()) }
                            f.alt?.let { put("alt", it) }
                            f.speed?.let { put("vel", it * 3.6) }
                        }
                    }).toString()
                    val auth = Base64.encodeToString("pingtray:${s.laptopToken}".toByteArray(), Base64.NO_WRAP)
                    val request = Request.Builder().url(s.laptopUrl)
                        .header("Authorization", "Basic $auth")
                        .post(body.toRequestBody("application/json".toMediaType()))
                        .build()
                    val ok = runCatching { http.newCall(request).execute().use { it.isSuccessful } }.getOrDefault(false)
                    lastUpload = if (ok) "sent ${batch.size}" else "laptop not reachable"
                    if (ok) {
                        app.db.markSent(batch.last().ts)
                        continue
                    }
                }
            }
            delay(30_000)
        }
    }

    private suspend fun notificationLoop() {
        val nm = getSystemService(NotificationManager::class.java)
        var last: String? = null
        while (lifecycleScope.isActive) {
            val status = Status.compute(this)
            // only when something visible changed
            if (status.signature != last) {
                nm.notify(NOTIFICATION, notification(status))
                last = status.signature
            }
            delay(5_000)
        }
    }

    private fun notification(status: Status?): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setContentTitle(status?.title ?: "pingtray")
            .setContentText(status?.text ?: "starting…")
        status?.graph?.let { g ->
            b.setLargeIcon(g)
            b.style = Notification.BigPictureStyle().bigPicture(g).setSummaryText(status.text)
        }
        return b.build()
    }

    companion object {
        const val CHANNEL = "status"
        const val NOTIFICATION = 1
        @Volatile var lastUpload: String? = null

        fun start(context: Context) {
            context.startForegroundService(Intent(context, ProbeService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ProbeService::class.java))
        }
    }
}
