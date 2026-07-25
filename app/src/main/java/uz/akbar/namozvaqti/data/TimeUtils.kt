package uz.akbar.namozvaqti.data

import java.util.Calendar
import java.util.TimeZone

/**
 * Mirror of the Python time_utils: everything anchored to Asia/Tashkent (UTC+5).
 * Uses java.util.Calendar (available since API 1) — no java.time, so no
 * core-library desugaring is needed on the old Android 7 device.
 */
object TimeUtils {
    val TZ: TimeZone = TimeZone.getTimeZone("Asia/Tashkent")

    /** epoch SECONDS for a "yyyy-MM-dd" + "HH:mm" pair, like Python build_timestamp. */
    fun buildTimestamp(dateStr: String, timeStr: String): Long {
        val (y, mo, d) = dateStr.split("-").map { it.toInt() }
        val (h, mi) = timeStr.split(":").map { it.toInt() }
        val cal = Calendar.getInstance(TZ)
        cal.clear()
        cal.set(y, mo - 1, d, h, mi, 0)
        return cal.timeInMillis / 1000L
    }

    fun todayKey(): String = dayKey(0)

    fun tomorrowKey(): String = dayKey(1)

    private fun dayKey(plusDays: Int): String {
        val cal = Calendar.getInstance(TZ)
        cal.add(Calendar.DAY_OF_MONTH, plusDays)
        return "%04d-%02d-%02d".format(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
        )
    }

    /**
     * "Now" in epoch seconds, rounded UP to the next whole second.
     *
     * An exact alarm may fire a couple of milliseconds BEFORE its trigger time.
     * With a truncating clock the prayer that is about to start still counts as
     * "in the future", so the scheduler re-arms the same alarm and the widget
     * arms its countdown with ~0 left (which then runs into negative numbers).
     * Rounding up makes the boundary inclusive for every caller.
     */
    fun nowSeconds(): Long = (System.currentTimeMillis() + 999L) / 1000L
}
