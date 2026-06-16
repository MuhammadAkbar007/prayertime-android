package ai.wakil.namozvaqti.data

import org.json.JSONArray

/**
 * Mirror of the Python parse.py. Maps Aladhan timing keys to internal english
 * keys and keeps the six core prayers. Tahajjud is intentionally omitted.
 */
object Parse {
    private val ALADHAN_KEYS = linkedMapOf(
        "Fajr" to "fajr",
        "Sunrise" to "sunrise",
        "Dhuhr" to "dhuhr",
        "Asr" to "asr",
        "Maghrib" to "maghrib",
        "Isha" to "isha",
    )

    /** Aladhan formats times like "04:44 (+05)" -> keep just "HH:MM". */
    private fun clean(t: String): String = t.split(" ")[0]

    /** Parse Aladhan's monthly `data` into {yyyy-MM-dd: {name: "HH:MM"}}. */
    fun parseMonth(data: JSONArray): LinkedHashMap<String, LinkedHashMap<String, String>> {
        val result = LinkedHashMap<String, LinkedHashMap<String, String>>()

        for (i in 0 until data.length()) {
            val day = data.getJSONObject(i)
            // gregorian.date is "DD-MM-YYYY"
            val greg = day.getJSONObject("date").getJSONObject("gregorian").getString("date")
            val (d, mo, y) = greg.split("-")
            val key = "$y-$mo-$d"

            val timings = day.getJSONObject("timings")
            val prayers = LinkedHashMap<String, String>()
            for ((al, en) in ALADHAN_KEYS) {
                if (timings.has(al)) prayers[en] = clean(timings.getString(al))
            }

            result[key] = prayers
        }

        if (result.isEmpty()) throw RuntimeException("Parsed data is empty")
        return result
    }
}
