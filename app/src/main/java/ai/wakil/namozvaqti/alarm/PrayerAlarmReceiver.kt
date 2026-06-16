package ai.wakil.namozvaqti.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ai.wakil.namozvaqti.data.Labels
import ai.wakil.namozvaqti.notify.Notifier
import ai.wakil.namozvaqti.widget.PrayerWidget

/** Fires at a prayer time: show the notification, then arm the next prayer. */
class PrayerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.getStringExtra(PrayerScheduler.EXTRA_NAME) ?: return
        val time = intent.getStringExtra(PrayerScheduler.EXTRA_TIME) ?: ""

        Notifier.showPrayer(context, Labels.display(name), time)

        // Re-arm the following prayer off the main thread (goAsync keeps the
        // process alive briefly so a quick cache/network read can finish).
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
