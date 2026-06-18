package uz.akbar.namozvaqti.data

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Mirror of the Python cache.py, but rooted at the app's internal files dir
 * (filesDir/namozvaqti/yyyy-MM-dd.json) instead of ~/.cache. One file per day so
 * the stale fallback (loadLatestBefore) works exactly like the Linux version.
 */
class Cache(context: Context) {
    private val dir = File(context.filesDir, "namozvaqti").apply { mkdirs() }

    fun saveDay(dateKey: String, day: Day) {
        val obj = JSONObject()
        for ((name, p) in day) {
            obj.put(name, JSONObject().put("time", p.time).put("timestamp", p.timestamp))
        }
        File(dir, "$dateKey.json").writeText(obj.toString())
    }

    fun loadDay(dateKey: String): Day? {
        val f = File(dir, "$dateKey.json")
        if (!f.exists()) return null
        return runCatching { parse(f.readText()) }.getOrNull()
    }

    /** Most recent cached day strictly before dateKey (filenames sort chronologically). */
    fun loadLatestBefore(dateKey: String): Pair<String, Day>? {
        val files = dir.listFiles { f -> f.name.endsWith(".json") } ?: return null
        val latest = files.map { it.name.removeSuffix(".json") }
            .filter { it < dateKey }
            .maxOrNull() ?: return null
        val day = loadDay(latest) ?: return null
        return latest to day
    }

    private fun parse(text: String): Day {
        val obj = JSONObject(text)
        val day = Day()
        val it = obj.keys()
        while (it.hasNext()) {
            val name = it.next()
            val p = obj.getJSONObject(name)
            day[name] = Prayer(p.getString("time"), p.getLong("timestamp"))
        }
        return day
    }
}
