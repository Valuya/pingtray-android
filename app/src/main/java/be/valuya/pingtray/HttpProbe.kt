package be.valuya.pingtray

import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * A tiny real HTTP request: HEAD on a fresh connection, timed phase by
 * phase, so that it can be compared with pings.
 */
object HttpProbe {
    private class Timer : EventListener() {
        var start = 0L
        var dnsStart = 0L; var dnsEnd = 0L
        var connectStart = 0L; var connectEnd = 0L
        var tlsStart = 0L; var tlsEnd = 0L
        var requestSent = 0L; var firstByte = 0L

        override fun callStart(call: Call) { start = System.nanoTime() }
        override fun dnsStart(call: Call, domainName: String) { dnsStart = System.nanoTime() }
        override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) { dnsEnd = System.nanoTime() }
        override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) { connectStart = System.nanoTime() }
        override fun secureConnectStart(call: Call) { tlsStart = System.nanoTime() }
        override fun secureConnectEnd(call: Call, handshake: Handshake?) { tlsEnd = System.nanoTime() }
        override fun connectEnd(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?) { connectEnd = System.nanoTime() }
        override fun requestHeadersEnd(call: Call, request: Request) { requestSent = System.nanoTime() }
        override fun responseHeadersStart(call: Call) { firstByte = System.nanoTime() }

        fun ms(a: Long, b: Long) = if (a == 0L || b == 0L) 0.0 else (b - a) / 1e6

        fun phases(): Phases {
            val tls = ms(tlsStart, tlsEnd)
            return Phases(
                dns = ms(dnsStart, dnsEnd),
                connect = maxOf(0.0, ms(connectStart, connectEnd) - tls),
                tls = tls,
                wait = ms(requestSent, firstByte),
            )
        }
    }

    /** Phases and HTTP status; null when the server could not be reached. */
    fun probe(url: String, timeoutMs: Int): Pair<Phases, Int>? {
        val timer = Timer()
        val client = OkHttpClient.Builder()
            .connectionPool(ConnectionPool(0, 1, TimeUnit.MILLISECONDS))
            .callTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .followRedirects(false)
            .retryOnConnectionFailure(false)
            .eventListener(timer)
            .build()
        val request = Request.Builder().url(url).head().header("User-Agent", "pingtray-android").build()
        return try {
            client.newCall(request).execute().use { r: Response -> timer.phases() to r.code }
        } catch (e: Exception) {
            null
        } finally {
            client.dispatcher.executorService.shutdown()
        }
    }
}
