package be.valuya.pingtray

/** Percentiles kept per bucket, every 5 %, so the smoke can fade smoothly. */
val PCTS = DoubleArray(21) { it * 0.05 }
const val MEDIAN = 10
const val P10 = 2
const val P90 = 18

/** The samples of one time slot: percentiles of the replies, and losses. */
class Bucket(val ts: Long, val pcts: DoubleArray?, val count: Int, val lost: Int) {
    val loss get() = if (count == 0) 0.0 else lost.toDouble() / count
    val median get() = pcts?.get(MEDIAN)
}

fun percentile(sorted: List<Double>, p: Double): Double {
    val pos = p * (sorted.size - 1)
    val lo = kotlin.math.floor(pos).toInt()
    val hi = kotlin.math.ceil(pos).toInt()
    return sorted[lo] + (sorted[hi] - sorted[lo]) * (pos - lo)
}

fun bucketize(samples: List<Pair<Long, Double?>>, bucketMs: Long): List<Bucket> {
    if (samples.isEmpty()) return emptyList()
    return samples.groupBy { Math.floorDiv(it.first, bucketMs) * bucketMs }.map { (ts, group) ->
        val ok = group.mapNotNull { it.second }.sorted()
        Bucket(ts, if (ok.isEmpty()) null else DoubleArray(PCTS.size) { percentile(ok, PCTS[it]) }, group.size, group.size - ok.size)
    }.sortedBy { it.ts }
}

/** Candidate bucket sizes, in seconds. */
val STEPS = longArrayOf(1, 2, 5, 10, 15, 30, 60, 120, 300, 600, 900, 1800, 3600, 7200, 21600, 43200, 86400)

fun bucketSeconds(spanSecs: Double, intervalSecs: Double, maxPoints: Int = 120): Long {
    val want = maxOf(spanSecs / maxPoints, intervalSecs * 5)
    return STEPS.firstOrNull { it >= want } ?: STEPS.last()
}

/** A round value above typical medians: rare spikes are clipped, as in SmokePing. */
fun niceTop(medians: List<Double>): Double {
    if (medians.isEmpty()) return 50.0
    val typical = percentile(medians.sorted(), 0.9) * 1.6
    var top = 1.0
    while (true) {
        for (m in doubleArrayOf(1.0, 2.0, 5.0)) if (m * top >= maxOf(typical, 5.0)) return m * top
        top *= 10
    }
}

fun fmtMs(ms: Double): String = when {
    ms < 10 -> "%.1f ms".format(ms)
    ms < 10_000 -> "%.0f ms".format(ms)
    else -> "%.0f s".format(ms / 1000)
}
