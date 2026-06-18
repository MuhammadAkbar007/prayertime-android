package uz.akbar.namozvaqti.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import uz.akbar.namozvaqti.widget.PrayerWidget

/** Alarms don't survive a reboot, so re-arm the next prayer after boot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pending = goAsync()
        val app = context.applicationContext
        Thread {
            try {
                PrayerScheduler.scheduleNext(app)
                PrayerWidget.updateAll(app)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
