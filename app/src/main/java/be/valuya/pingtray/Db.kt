package be.valuya.pingtray

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Fix(val ts: Long, val lat: Double, val lon: Double, val acc: Float?, val alt: Double?, val speed: Float?)
data class Note(val id: Long, val ts: Long, val text: String)
/** DNS, connect, TLS and answer times in ms. */
data class Phases(val dns: Double, val connect: Double, val tls: Double, val wait: Double) {
    val total get() = dns + connect + tls + wait
}

/** Everything measured, in one SQLite file. */
class Db(context: Context) : SQLiteOpenHelper(context, "pingtray.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE sample (target TEXT NOT NULL, kind TEXT NOT NULL, ts INTEGER NOT NULL,
               rtt_us INTEGER, PRIMARY KEY (target, kind, ts)) WITHOUT ROWID"""
        )
        db.execSQL(
            """CREATE TABLE http_detail (target TEXT NOT NULL, ts INTEGER NOT NULL, dns_us INTEGER,
               connect_us INTEGER, tls_us INTEGER, wait_us INTEGER, status INTEGER,
               PRIMARY KEY (target, ts)) WITHOUT ROWID"""
        )
        db.execSQL(
            """CREATE TABLE fix (ts INTEGER PRIMARY KEY, lat REAL NOT NULL, lon REAL NOT NULL,
               acc REAL, alt REAL, speed REAL, sent INTEGER NOT NULL DEFAULT 0)"""
        )
        db.execSQL("CREATE INDEX fix_unsent ON fix(sent, ts)")
        db.execSQL("CREATE TABLE note (id INTEGER PRIMARY KEY, ts INTEGER NOT NULL, text TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    override fun onConfigure(db: SQLiteDatabase) {
        db.enableWriteAheadLogging()
    }

    fun insertSample(target: String, kind: String, ts: Long, rttUs: Long?) {
        writableDatabase.insertWithOnConflict("sample", null, ContentValues().apply {
            put("target", target); put("kind", kind); put("ts", ts); put("rtt_us", rttUs)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun insertHttp(target: String, ts: Long, p: Phases, status: Int) {
        writableDatabase.insertWithOnConflict("http_detail", null, ContentValues().apply {
            put("target", target); put("ts", ts)
            put("dns_us", (p.dns * 1000).toLong()); put("connect_us", (p.connect * 1000).toLong())
            put("tls_us", (p.tls * 1000).toLong()); put("wait_us", (p.wait * 1000).toLong())
            put("status", status)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** (ts, rtt in ms or null when lost), oldest first. */
    fun samples(target: String, kind: String, from: Long, to: Long): List<Pair<Long, Double?>> =
        readableDatabase.rawQuery(
            "SELECT ts, rtt_us FROM sample WHERE target = ? AND kind = ? AND ts >= ? AND ts < ? ORDER BY ts",
            arrayOf(target, kind, from.toString(), to.toString())
        ).use { c ->
            buildList { while (c.moveToNext()) add(c.getLong(0) to (if (c.isNull(1)) null else c.getLong(1) / 1000.0)) }
        }

    fun phases(target: String, from: Long, to: Long): List<Pair<Long, Phases>> =
        readableDatabase.rawQuery(
            "SELECT ts, dns_us, connect_us, tls_us, wait_us FROM http_detail WHERE target = ? AND ts >= ? AND ts < ? ORDER BY ts",
            arrayOf(target, from.toString(), to.toString())
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    c.getLong(0) to Phases(c.getLong(1) / 1000.0, c.getLong(2) / 1000.0, c.getLong(3) / 1000.0, c.getLong(4) / 1000.0)
                )
            }
        }

    fun insertFix(f: Fix) {
        writableDatabase.insertWithOnConflict("fix", null, ContentValues().apply {
            put("ts", f.ts); put("lat", f.lat); put("lon", f.lon)
            put("acc", f.acc); put("alt", f.alt); put("speed", f.speed)
        }, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun fixes(from: Long, to: Long): List<Fix> =
        readableDatabase.rawQuery(
            "SELECT ts, lat, lon, acc, alt, speed FROM fix WHERE ts >= ? AND ts < ? ORDER BY ts",
            arrayOf(from.toString(), to.toString())
        ).use { c -> buildList { while (c.moveToNext()) add(c.fix()) } }

    fun unsentFixes(limit: Int): List<Fix> =
        readableDatabase.rawQuery(
            "SELECT ts, lat, lon, acc, alt, speed FROM fix WHERE sent = 0 ORDER BY ts LIMIT $limit", null
        ).use { c -> buildList { while (c.moveToNext()) add(c.fix()) } }

    fun markSent(upTo: Long) {
        writableDatabase.execSQL("UPDATE fix SET sent = 1 WHERE sent = 0 AND ts <= ?", arrayOf(upTo))
    }

    fun unsentCount(): Long =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM fix WHERE sent = 0", null).use { c -> c.moveToFirst(); c.getLong(0) }

    private fun android.database.Cursor.fix() = Fix(
        getLong(0), getDouble(1), getDouble(2),
        if (isNull(3)) null else getFloat(3), if (isNull(4)) null else getDouble(4), if (isNull(5)) null else getFloat(5)
    )

    fun addNote(ts: Long, text: String) {
        writableDatabase.insert("note", null, ContentValues().apply { put("ts", ts); put("text", text) })
    }

    fun deleteNote(id: Long) {
        writableDatabase.delete("note", "id = ?", arrayOf(id.toString()))
    }

    fun notes(from: Long, to: Long): List<Note> =
        readableDatabase.rawQuery(
            "SELECT id, ts, text FROM note WHERE ts >= ? AND ts < ? ORDER BY ts", arrayOf(from.toString(), to.toString())
        ).use { c -> buildList { while (c.moveToNext()) add(Note(c.getLong(0), c.getLong(1), c.getString(2))) } }

    fun recentNotes(limit: Int): List<Note> =
        readableDatabase.rawQuery("SELECT id, ts, text FROM note ORDER BY ts DESC LIMIT $limit", null)
            .use { c -> buildList { while (c.moveToNext()) add(Note(c.getLong(0), c.getLong(1), c.getString(2))) } }

    fun frequentNotes(limit: Int): List<String> =
        readableDatabase.rawQuery(
            "SELECT text FROM note GROUP BY text ORDER BY COUNT(*) DESC, MAX(ts) DESC LIMIT $limit", null
        ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    fun purgeBefore(ts: Long) {
        writableDatabase.execSQL("DELETE FROM sample WHERE ts < ?", arrayOf(ts))
        writableDatabase.execSQL("DELETE FROM http_detail WHERE ts < ?", arrayOf(ts))
        writableDatabase.execSQL("DELETE FROM fix WHERE ts < ? AND sent = 1", arrayOf(ts))
    }
}
