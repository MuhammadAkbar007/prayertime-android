package uz.akbar.namozvaqti.data

/** Mirror of the Python transform.py: attach epoch timestamps to each time. */
object Transform {
    fun enrich(parsed: Map<String, Map<String, String>>): LinkedHashMap<String, Day> {
        val result = LinkedHashMap<String, Day>()
        for ((date, prayers) in parsed) {
            val day = Day()
            for ((name, timeStr) in prayers) {
                val key = name.lowercase()
                day[key] = Prayer(timeStr, TimeUtils.buildTimestamp(date, timeStr))
            }
            result[date] = day
        }
        return result
    }
}
