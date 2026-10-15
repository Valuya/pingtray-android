package be.valuya.pingtray

import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress

/**
 * Real ICMP echo without root: Android lets apps open "ping sockets"
 * (SOCK_DGRAM + IPPROTO_ICMP), the kernel fills in identifier and checksum.
 */
object Icmp {
    private var seq = 0

    /** Round trip in µs, or null when nothing came back in time. */
    fun ping(addr: InetAddress, timeoutMs: Int): Long? {
        val v6 = addr is Inet6Address
        val fd = Os.socket(
            if (v6) OsConstants.AF_INET6 else OsConstants.AF_INET,
            OsConstants.SOCK_DGRAM,
            if (v6) OsConstants.IPPROTO_ICMPV6 else OsConstants.IPPROTO_ICMP,
        )
        try {
            val s = (seq++ and 0xffff)
            val packet = ByteArray(8 + 24)
            packet[0] = (if (v6) 128 else 8).toByte() // echo request
            packet[6] = (s shr 8).toByte()
            packet[7] = s.toByte()
            "pingtray-android-probe!!".toByteArray().copyInto(packet, 8)
            val start = System.nanoTime()
            Os.sendto(fd, packet, 0, packet.size, 0, addr, 0)
            val buf = ByteArray(1500)
            while (true) {
                val left = timeoutMs - ((System.nanoTime() - start) / 1_000_000).toInt()
                if (left <= 0) return null
                val pfd = StructPollfd().apply { this.fd = fd; events = OsConstants.POLLIN.toShort() }
                if (Os.poll(arrayOf(pfd), left) <= 0) return null
                val n = Os.recvfrom(fd, buf, 0, buf.size, 0, InetSocketAddress(0))
                val reply = (if (v6) 129 else 0).toByte()
                if (n >= 8 && buf[0] == reply && ((buf[6].toInt() and 0xff) shl 8 or (buf[7].toInt() and 0xff)) == s) {
                    return (System.nanoTime() - start) / 1000
                }
            }
        } catch (e: Exception) {
            return null
        } finally {
            Os.close(fd)
        }
    }
}
