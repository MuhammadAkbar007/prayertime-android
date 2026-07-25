package uz.akbar.namozvaqti.widget

import android.app.AlarmManager
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
                updateAll(app)
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
        private const val REQ_REFRESH = 43

        /** Refresh all placed widgets. Safe to call from any thread (blocking). */
        fun updateAll(context: Context) {
            val app = context.applicationContext
            val mgr = AppWidgetManager.getInstance(app)
            val ids = mgr.getAppWidgetIds(ComponentName(app, PrayerWidget::class.java))
            if (ids.isEmpty()) return
            // A corrupt cache entry must not take the whole process down: every
            // caller runs this on a bare Thread, where a throw is fatal. Leaving
            // the previous views up is the better failure.
            // Nothing here may take the process down: every caller runs this on a
            // bare Thread, where a throw is fatal — and a dead process means the
            // countdown already on screen keeps ticking, straight past zero into
            // negative numbers. Leaving the previous views up is the better failure.
            try {
                mgr.updateAppWidget(ids, buildViews(app, ids))
            } catch (t: Throwable) {
                // corrupt cache entry, OOM, oversized Binder transaction…
            }
        }

        /**
         * Wake up once at the next prayer to redraw. The countdown baked into the
         * Chronometer is only correct until that instant — past it the widget
         * would count *up* and render a bogus negative time until something else
         * happened to refresh it (up to 30 min, per updatePeriodMillis).
         *
         * RTC, not RTC_WAKEUP: nobody reads a widget on a sleeping screen, so this
         * lands on the next wake-up instead of costing battery on an old phone.
         */
        private fun armRefresh(context: Context, ids: IntArray, atMillis: Long) {
            val intent = Intent(context, PrayerWidget::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            val pi = PendingIntent.getBroadcast(
                context, REQ_REFRESH, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            // Never in the past — that would re-fire instantly, forever.
            val at = atMillis.coerceAtLeast(System.currentTimeMillis() + 5_000L)
            context.getSystemService(AlarmManager::class.java).setExact(AlarmManager.RTC, at, pi)
        }

        private fun buildViews(context: Context, ids: IntArray): RemoteViews {
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

            val service = PrayerService(Cache(context))
            val day: Day? = try { service.getTodayResilient().first } catch (t: Throwable) { null }

            // Read the clock AFTER the load above: on a cache miss it fetches the
            // month over the network and can burn several seconds — long enough to
            // step over the next prayer. A "now" from before that would arm the
            // countdown with a deadline already in the past.
            val todayKey = TimeUtils.todayKey()
            val now = TimeUtils.nowSeconds()
            views.setTextViewText(R.id.tvWDate, DateFmt.header(todayKey))

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
                // Before today's first prayer the interval started at last night's
                // Isha. A day missing Isha is a corrupt cache entry — fall back to
                // a full ring rather than throwing away the whole update.
                val prevTs = passed?.timestamp
                    ?: day["isha"]?.let { it.timestamp - 86400 }
                    ?: (nextPrayer.timestamp - 86400)
                val span = (nextPrayer.timestamp - prevTs).coerceAtLeast(1)
                // Fraction of the interval still REMAINING until the next prayer.
                // The ring starts full and depletes as the prayer approaches.
                val remaining = ((nextPrayer.timestamp - now).toFloat() / span).coerceIn(0f, 1f)

                // Drawn smaller than its slot and scaled up by the ImageView:
                // the bitmap travels to the launcher over Binder inside the
                // RemoteViews, and a full-size ARGB_8888 one (~460 KB on this
                // phone) is big enough to blow the 1 MB transaction budget — when
                // that happens the whole update is dropped and the stale
                // Chronometer keeps ticking into negative numbers.
                val sizePx = (128 * context.resources.displayMetrics.density).toInt().coerceIn(96, 256)
                views.setImageViewBitmap(R.id.ring, ringBitmap(sizePx, remaining, gold))

                views.setTextViewText(R.id.nmNext, Labels.display(nextKey))
                views.setTextViewText(R.id.tmNext, nextPrayer.time)
                // Live countdown without widget refreshes (API 24 countdown chrono).
                // Guaranteed positive: `now` is rounded up, so nextPrayer is at
                // least one whole second away.
                val remMs = nextPrayer.timestamp * 1000L - System.currentTimeMillis()
                views.setChronometer(R.id.chrono, SystemClock.elapsedRealtime() + remMs, "-%s", true)
                views.setChronometerCountDown(R.id.chrono, true)

                // ...and redraw when it expires, so it never counts past zero.
                armRefresh(context, ids, nextPrayer.timestamp * 1000L + 1_500L)
            } else {
                views.setTextViewText(R.id.nmNext, "")
                views.setTextViewText(R.id.tmNext, "--:--")
            }

            return views
        }

        /**
         * A circular countdown ring: faint full track + a gold arc whose length is
         * the fraction of time still REMAINING until the next prayer. It starts as a
         * full gold circle right after a prayer and depletes (clockwise from the top)
         * as the next prayer nears, so the visible gold = how much time is left.
         */
        private fun ringBitmap(size: Int, remaining: Float, ringColor: Int): Bitmap {
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val stroke = size * 0.08f
            // Only enough margin to keep the round cap from clipping — the arc
            // should fill the bitmap, since the bitmap fills its slot.
            val inset = stroke / 2f + size * 0.01f
            val rect = RectF(inset, inset, size - inset, size - inset)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke
                strokeCap = Paint.Cap.ROUND
            }
            paint.color = Color.parseColor("#33FFFFFF")
            canvas.drawArc(rect, 0f, 360f, false, paint)
            paint.color = ringColor
            canvas.drawArc(rect, -90f, 360f * remaining, false, paint)
            return bmp
        }
    }
}
