package ai.wakil.namozvaqti.data

import java.util.Calendar

/** English date formatting via Calendar (no java.time, no locale dependency). */
object DateFmt {
    private val MONTHS = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )

    // Indexed by Calendar.DAY_OF_WEEK: SUNDAY=1 .. SATURDAY=7 (slot 0 unused).
    private val WEEK_FULL = listOf(
        "", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday",
    )

    private fun calOf(dateKey: String): Calendar {
        val (y, mo, d) = dateKey.split("-").map { it.toInt() }
        val cal = Calendar.getInstance(TimeUtils.TZ)
        cal.clear()
        cal.set(y, mo - 1, d)
        return cal
    }

    /** "Wednesday, June 17" */
    fun header(dateKey: String): String {
        val c = calOf(dateKey)
        val dom = c.get(Calendar.DAY_OF_MONTH)
        val mon = MONTHS[c.get(Calendar.MONTH)]
        val wd = WEEK_FULL[c.get(Calendar.DAY_OF_WEEK)]
        return "$wd, $mon $dom"
    }

    /** "Wed · Jun 17" */
    fun short(dateKey: String): String {
        val c = calOf(dateKey)
        val dom = c.get(Calendar.DAY_OF_MONTH)
        val mon = MONTHS[c.get(Calendar.MONTH)].take(3)
        val wd = WEEK_FULL[c.get(Calendar.DAY_OF_WEEK)].take(3)
        return "$wd · $mon $dom"
    }
}
