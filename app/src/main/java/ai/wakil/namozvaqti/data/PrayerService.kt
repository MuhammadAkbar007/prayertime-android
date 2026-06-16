package ai.wakil.namozvaqti.data

import java.util.Calendar

/**
 * Mirror of the Python service.py. Same prayer order, same next-prayer pick,
 * and the SAME stale fallback: when today can't be fetched (offline + not
 * cached), fall back to the most recent cached day re-stamped onto today's clock
 * (see getNextPrayerResilient / rebuildForDate / loadLatestBefore).
 */
class PrayerService(private val cache: Cache) {

    companion object {
        // Sunrise is an informational row; included so the countdown also stops
        // on it.
        val PRAYER_ORDER = listOf("fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha")
    }

    data class Next(val name: String, val prayer: Prayer, val stale: Boolean)

    /** Cache hit, else fetch the whole month and cache every day. */
    fun ensureDay(dateKey: String): Day {
        cache.loadDay(dateKey)?.let { return it }

        val parts = dateKey.split("-").map { it.toInt() }
        val data = Fetch.fetchMonth(parts[0], parts[1])
        val parsed = Parse.parseMonth(data)
        val enriched = Transform.enrich(parsed)

        for ((k, day) in enriched) cache.saveDay(k, day)

        return enriched[dateKey] ?: throw RuntimeException("Fetched month had no entry for $dateKey")
    }

    fun getDay(dateKey: String): Day = ensureDay(dateKey)

    /** Pick the next prayer from an already-loaded day (no fetching). */
    fun nextPrayerForDay(day: Day, nowSec: Long): Pair<String, Prayer> {
        for (name in PRAYER_ORDER) {
            val p = day[name]
            if (p != null && p.timestamp > nowSec) return name to p
        }
        // After Isha -> tomorrow's Fajr, approximated as today's Fajr + 24h.
        val fajr = day["fajr"]!!
        return "fajr" to Prayer(fajr.time, fajr.timestamp + 86400)
    }

    /** Re-stamp a day's time strings onto another date's clock (stale fallback). */
    fun rebuildForDate(day: Day, dateKey: String): Day {
        val out = Day()
        for ((name, p) in day) {
            out[name] = Prayer(p.time, TimeUtils.buildTimestamp(dateKey, p.time))
        }
        return out
    }

    /** Next prayer with the resilient stale fallback. Raises only if nothing is cached. */
    fun getNextPrayerResilient(): Next {
        val todayKey = TimeUtils.todayKey()
        val now = TimeUtils.nowSeconds()
        return try {
            val (name, p) = nextPrayerForDay(getDay(todayKey), now)
            Next(name, p, stale = false)
        } catch (e: Exception) {
            val stale = cache.loadLatestBefore(todayKey) ?: throw e
            val approx = rebuildForDate(stale.second, todayKey)
            val (name, p) = nextPrayerForDay(approx, now)
            Next(name, p, stale = true)
        }
    }

    /** Today's full day with the same stale fallback, for the UI list. */
    fun getTodayResilient(): Pair<Day, Boolean> {
        val todayKey = TimeUtils.todayKey()
        return try {
            getDay(todayKey) to false
        } catch (e: Exception) {
            val stale = cache.loadLatestBefore(todayKey) ?: throw e
            rebuildForDate(stale.second, todayKey) to true
        }
    }

    /** The current month for the month view. Triggers a fetch if needed, but
     *  never throws — returns whatever days are cached. */
    fun getMonth(): LinkedHashMap<String, Day> {
        runCatching { ensureDay(TimeUtils.todayKey()) } // best-effort fetch
        val cal = Calendar.getInstance(TimeUtils.TZ)
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) // 0-based
        val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val result = LinkedHashMap<String, Day>()
        for (d in 1..days) {
            val key = "%04d-%02d-%02d".format(y, m + 1, d)
            cache.loadDay(key)?.let { result[key] = it }
        }
        return result
    }
}
