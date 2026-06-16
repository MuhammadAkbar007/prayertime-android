package ai.wakil.namozvaqti.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Mirror of the Python fetch.py. Same Aladhan parameters, tuned to reproduce the
 * official ISLOM.UZ (Muslim Board of Uzbekistan) Namangan calendar:
 *
 *   method=99, methodSettings=15.5,null,15.5, school=1 (Hanafi Asr),
 *   timezonestring=Asia/Tashkent, tune=0,0,0,0,0,4,0,0,0 (+4 min Maghrib)
 *
 * Aladhan serves a whole month per call, so one success runs offline for the
 * rest of the month (see PrayerService.ensureDay, which caches every day).
 */
object Fetch {
    private const val LAT = 41.0058
    private const val LNG = 71.6436

    private val PARAMS = linkedMapOf(
        "latitude" to LAT.toString(),
        "longitude" to LNG.toString(),
        "method" to "99",
        "methodSettings" to "15.5,null,15.5",
        "school" to "1",
        "timezonestring" to "Asia/Tashkent",
        "tune" to "0,0,0,0,0,4,0,0,0",
    )

    /** Fetch one month's `data` array. 3 attempts, 8s timeouts, linear backoff. */
    fun fetchMonth(year: Int, month: Int): JSONArray {
        val query = PARAMS.entries.joinToString("&") {
            "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}"
        }
        val urlStr = "https://api.aladhan.com/v1/calendar/$year/$month?$query"

        val attempts = 3
        for (i in 0 until attempts) {
            try {
                val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 8000
                }
                try {
                    if (conn.responseCode != 200) {
                        throw RuntimeException("Aladhan HTTP ${conn.responseCode}")
                    }
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val data = JSONObject(body).optJSONArray("data")
                        ?: throw RuntimeException("Aladhan response missing 'data'")
                    if (data.length() == 0) throw RuntimeException("Aladhan 'data' empty")
                    return data
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                if (i == attempts - 1) throw e
                try {
                    Thread.sleep(1000L * (i + 1)) // linear backoff
                } catch (_: InterruptedException) {
                }
            }
        }
        throw RuntimeException("Unreachable: fetchMonth exhausted retries")
    }
}
