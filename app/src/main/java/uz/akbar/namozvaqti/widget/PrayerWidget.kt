package uz.akbar.namozvaqti.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import uz.akbar.namozvaqti.PrayerIcons
import uz.akbar.namozvaqti.R
import uz.akbar.namozvaqti.data.Cache
import uz.akbar.namozvaqti.data.DateFmt
import uz.akbar.namozvaqti.data.Day
import uz.akbar.namozvaqti.data.Labels
import uz.akbar.namozvaqti.data.PrayerService
import uz.akbar.namozvaqti.data.TimeUtils
import uz.akbar.namozvaqti.ui.MainActivity

/**
 * Home-screen widget: dark glassy, semi-transparent panel showing the next
 * prayer (name + time) and all of today's times. Static — no ticking.
 * Reads from the same per-day cache (fetches if missing, off the main thread).
 */
class PrayerWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        // Building the views may touch disk/network — do it off the main thread.
        val pending = goAsync()
        val app = context.applicationContext
        Thread {
            try {
                val views = buildViews(app)
                manager.updateAppWidget(appWidgetIds, views)
            } finally {
                pending.finish()
            }
        }.start()
    }

    companion object {
        private val ROW = intArrayOf(R.id.row0, R.id.row1, R.id.row2, R.id.row3, R.id.row4, R.id.row5)
        private val IC = intArrayOf(R.id.ic0, R.id.ic1, R.id.ic2, R.id.ic3, R.id.ic4, R.id.ic5)
        private val NM = intArrayOf(R.id.nm0, R.id.nm1, R.id.nm2, R.id.nm3, R.id.nm4, R.id.nm5)
        private val TM = intArrayOf(R.id.tm0, R.id.tm1, R.id.tm2, R.id.tm3, R.id.tm4, R.id.tm5)

        /** Refresh all placed widgets. Safe to call from any thread. */
        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, PrayerWidget::class.java))
            if (ids.isEmpty()) return
            mgr.updateAppWidget(ids, buildViews(context.applicationContext))
        }

        private fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_prayer)

            val openPi = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java)
                    .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, openPi)

            val gold = ContextCompat.getColor(context, R.color.accent)
            val primary = ContextCompat.getColor(context, R.color.text_primary)
            val secondary = ContextCompat.getColor(context, R.color.text_secondary)

            val todayKey = TimeUtils.todayKey()
            val now = TimeUtils.nowSeconds()
            views.setTextViewText(R.id.tvWDate, DateFmt.header(todayKey))

            val service = PrayerService(Cache(context))
            val day: Day? = try { service.getTodayResilient().first } catch (t: Throwable) { null }

            // Left column: today's prayer times, current prayer highlighted.
            val currentKey = day?.let { PrayerService.currentPrayerKey(it, now) }
            for (i in PrayerService.PRAYER_ORDER.indices) {
                val key = PrayerService.PRAYER_ORDER[i]
                val on = key == currentKey
                views.setImageViewResource(IC[i], PrayerIcons.of(key))
                views.setInt(IC[i], "setColorFilter", if (on) gold else secondary)
                views.setTextViewText(NM[i], Labels.display(key))
                views.setTextViewText(TM[i], day?.get(key)?.time ?: "--:--")
                views.setTextColor(NM[i], if (on) gold else secondary)
                views.setTextColor(TM[i], if (on) gold else primary)
                views.setInt(ROW[i], "setBackgroundResource", if (on) R.drawable.widget_row_next else 0)
            }

            // Right: circular countdown ring for the next prayer.
            if (day != null) {
                val (nextKey, nextPrayer) = service.nextPrayerForDay(day, now)
                // Progress = fraction elapsed from the previous prayer to the next.
                val passed = PrayerService.PRAYER_ORDER
                    .mapNotNull { day[it] }
                    .filter { it.timestamp <= now }
                    .maxByOrNull { it.timestamp }
                val prevTs = passed?.timestamp ?: (day["isha"]!!.timestamp - 86400)
                val span = (nextPrayer.timestamp - prevTs).coerceAtLeast(1)
                val progress = ((now - prevTs).toFloat() / span).coerceIn(0f, 1f)

                val sizePx = (170 * context.resources.displayMetrics.density).toInt()
                views.setImageViewBitmap(R.id.ring, ringBitmap(sizePx, progress, gold))

                views.setTextViewText(R.id.nmNext, Labels.display(nextKey))
                views.setTextViewText(R.id.tmNext, nextPrayer.time)
                // Live countdown without widget refreshes (API 24 countdown chrono).
                val remMs = nextPrayer.timestamp * 1000L - System.currentTimeMillis()
                views.setChronometer(R.id.chrono, SystemClock.elapsedRealtime() + remMs, "-%s", true)
                views.setChronometerCountDown(R.id.chrono, true)
            } else {
                views.setTextViewText(R.id.nmNext, "")
                views.setTextViewText(R.id.tmNext, "--:--")
            }

            return views
        }

        /** A circular progress ring: faint full track + gold arc from the top. */
        private fun ringBitmap(size: Int, progress: Float, ringColor: Int): Bitmap {
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val stroke = size * 0.08f
            val inset = stroke / 2f + size * 0.04f
            val rect = RectF(inset, inset, size - inset, size - inset)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke
                strokeCap = Paint.Cap.ROUND
            }
            paint.color = Color.parseColor("#33FFFFFF")
            canvas.drawArc(rect, 0f, 360f, false, paint)
            paint.color = ringColor
            canvas.drawArc(rect, -90f, 360f * progress, false, paint)
            return bmp
        }
    }
}
