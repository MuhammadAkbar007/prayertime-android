package ai.wakil.namozvaqti.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import ai.wakil.namozvaqti.data.Cache
import ai.wakil.namozvaqti.data.PrayerService

/**
 * Event-driven scheduling — the Android equivalent of the Python scheduler that
 * sleeps until the next prayer. We arm ONE exact alarm for the next prayer; when
 * it fires the receiver shows the notification and arms the following one. Zero
 * polling, zero battery drain between prayers.
 */
object PrayerScheduler {
    const val EXTRA_NAME = "name"
    const val EXTRA_TIME = "time"
    private const val REQ = 42

    /** Compute the next prayer (resilient) and arm an exact alarm for it.
     *  Does blocking work (cache/network) — call off the main thread. */
    fun scheduleNext(context: Context) {
        val service = PrayerService(Cache(context.applicationContext))
        val next = try {
            service.getNextPrayerResilient()
        } catch (e: Exception) {
            return // nothing cached and offline — try again next launch/boot
        }

        val triggerAtMillis = next.prayer.timestamp * 1000L
        val am = context.getSystemService(AlarmManager::class.java)

        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            putExtra(EXTRA_NAME, next.name)
            putExtra(EXTRA_TIME, next.prayer.time)
        }
        val pi = PendingIntent.getBroadcast(
            context, REQ, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.canScheduleExactAlarms()
        } else {
            true
        }

        try {
            if (canExact) {
                // setExactAndAllowWhileIdle survives Doze (API 23+).
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }
}
