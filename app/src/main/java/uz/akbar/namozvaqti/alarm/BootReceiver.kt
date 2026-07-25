package uz.akbar.namozvaqti.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import uz.akbar.namozvaqti.widget.PrayerWidget

/**
 * Re-arm the next prayer whenever the timeline underneath it moved: alarms don't
 * survive a reboot, and an alarm/countdown armed against a wrong clock (this
 * phone's RTC can boot skewed and get corrected later) is wrong until re-armed.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        Thread {
            try {
                PrayerScheduler.scheduleNext(app)
                PrayerWidget.updateAll(app)
            } catch (t: Throwable) {
                // never crash the process from a bare Thread
            } finally {
                pending.finish()
            }
        }.start()
    }
}
