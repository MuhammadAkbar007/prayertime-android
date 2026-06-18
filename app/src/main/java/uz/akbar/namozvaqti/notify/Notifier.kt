package uz.akbar.namozvaqti.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import uz.akbar.namozvaqti.R
import uz.akbar.namozvaqti.ui.MainActivity

/** Mirror of the Python notify.py: a notification + the bundled sound per prayer. */
object Notifier {
    private const val CHANNEL_ID = "prayer_times"
    private const val NOTIF_ID = 1001
    private val VIBRATION = longArrayOf(0, 450, 250, 450)

    private fun soundUri(context: Context): Uri =
        Uri.parse("android.resource://${context.packageName}/${R.raw.prayer_notification}")

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val channel = NotificationChannel(
                CHANNEL_ID, "Prayer times", NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Alerts at each prayer time"
                setSound(soundUri(context), attrs)
                enableVibration(true)
                vibrationPattern = VIBRATION
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    /** displayName already localized (e.g. "Bomdod"); time like "04:44". */
    fun showPrayer(context: Context, displayName: String, time: String) {
        ensureChannel(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPi = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_prayer)
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
            .setContentTitle("$displayName time")
            .setContentText("It's time for $displayName · $time")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentPi)
            .setAutoCancel(true)

        // Pre-O channels don't carry sound/vibration; attach them directly.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setSound(soundUri(context))
            builder.setVibrate(VIBRATION)
        }

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID, builder.build())
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted (API 33+) — nothing else to do here.
        }
    }
}
