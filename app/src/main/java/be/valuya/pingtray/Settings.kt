package be.valuya.pingtray

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A destination: pinged at [host], optionally probed over HTTP at [http]. */
data class Target(val name: String, val host: String, val http: String?)

/** Kept in SharedPreferences; edited in the Settings tab. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("pingtray", Context.MODE_PRIVATE)

    var targets: List<Target>
        get() = prefs.getString("targets", null)?.let(::parseTargets) ?: DEFAULT_TARGETS
        set(v) = prefs.edit().putString("targets", JSONArray(v.map {
            JSONObject().put("name", it.name).put("host", it.host).put("http", it.http ?: "")
        }).toString()).apply()

    var intervalSecs: Float
        get() = prefs.getFloat("interval", 2f)
        set(v) = prefs.edit().putFloat("interval", v).apply()
    var httpIntervalSecs: Float
        get() = prefs.getFloat("httpInterval", 10f)
        set(v) = prefs.edit().putFloat("httpInterval", v).apply()
    var timeoutMs: Int
        get() = prefs.getInt("timeout", 1500)
        set(v) = prefs.edit().putInt("timeout", v).apply()
    var httpTimeoutMs: Int
        get() = prefs.getInt("httpTimeout", 5000)
        set(v) = prefs.edit().putInt("httpTimeout", v).apply()
    var warnMs: Float
        get() = prefs.getFloat("warn", 60f)
        set(v) = prefs.edit().putFloat("warn", v).apply()
    var badMs: Float
        get() = prefs.getFloat("bad", 200f)
        set(v) = prefs.edit().putFloat("bad", v).apply()

    /** pingtray on the laptop: where to send positions (OwnTracks format). */
    var laptopUrl: String
        get() = prefs.getString("laptopUrl", "") ?: ""
        set(v) = prefs.edit().putString("laptopUrl", v.trim()).apply()
    var laptopToken: String
        get() = prefs.getString("laptopToken", "") ?: ""
        set(v) = prefs.edit().putString("laptopToken", v.trim()).apply()

    /** The series drawn in full: "name|ping" or "name|http". */
    var reference: String?
        get() = prefs.getString("reference", null)
        set(v) = prefs.edit().putString("reference", v).apply()

    var running: Boolean
        get() = prefs.getBoolean("running", true)
        set(v) = prefs.edit().putBoolean("running", v).apply()

    companion object {
        val DEFAULT_TARGETS = listOf(
            Target("UCLouvain", "130.104.1.1", "https://uclouvain.be/"),
            Target("Google DNS", "8.8.8.8", "https://www.google.com/generate_204"),
            Target("Cloudflare", "1.1.1.1", "https://cp.cloudflare.com/generate_204"),
        )

        fun parseTargets(json: String): List<Target> = runCatching {
            val a = JSONArray(json)
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                Target(o.getString("name"), o.getString("host"), o.optString("http").ifBlank { null })
            }
        }.getOrDefault(DEFAULT_TARGETS)
    }
}
