package ai.wakil.namozvaqti.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import ai.wakil.namozvaqti.PrayerIcons
import ai.wakil.namozvaqti.R
import ai.wakil.namozvaqti.data.Cache
import ai.wakil.namozvaqti.data.DateFmt
import ai.wakil.namozvaqti.data.Day
import ai.wakil.namozvaqti.data.Labels
import ai.wakil.namozvaqti.data.PrayerService
import ai.wakil.namozvaqti.data.TimeUtils
import ai.wakil.namozvaqti.ui.MainActivity

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

            // The "next" prayer still ahead today (for the row highlight).
            var nextKey: String? = null
            if (day != null) {
                for (k in PrayerService.PRAYER_ORDER) {
                    val p = day[k]
                    if (p != null && p.timestamp > now) { nextKey = k; break }
                }
            }

            for (i in PrayerService.PRAYER_ORDER.indices) {
                val key = PrayerService.PRAYER_ORDER[i]
                val p = day?.get(key)
                val isNext = key == nextKey
                views.setImageViewResource(IC[i], PrayerIcons.of(key))
                views.setInt(IC[i], "setColorFilter", if (isNext) gold else secondary)
                views.setTextViewText(NM[i], Labels.display(key))
                views.setTextViewText(TM[i], p?.time ?: "--:--")
                views.setTextColor(NM[i], if (isNext) gold else primary)
                views.setTextColor(TM[i], if (isNext) gold else primary)
                views.setInt(ROW[i], "setBackgroundResource", if (isNext) R.drawable.widget_row_next else 0)
            }

            return views
        }
    }
}
