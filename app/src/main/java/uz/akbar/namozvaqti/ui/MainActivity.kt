package uz.akbar.namozvaqti.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import uz.akbar.namozvaqti.R
import uz.akbar.namozvaqti.alarm.PrayerScheduler
import uz.akbar.namozvaqti.data.DateFmt
import uz.akbar.namozvaqti.data.Labels
import uz.akbar.namozvaqti.data.PrayerService
import uz.akbar.namozvaqti.data.Repo
import uz.akbar.namozvaqti.data.TimeUtils
import uz.akbar.namozvaqti.databinding.ActivityMainBinding
import uz.akbar.namozvaqti.PrayerIcons
import uz.akbar.namozvaqti.notify.Notifier
import uz.akbar.namozvaqti.widget.PrayerWidget
import android.widget.Toast

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var repo: Repo
    private val todayAdapter = TodayAdapter()
    private val monthAdapter = MonthAdapter()

    private val ticker = Handler(Looper.getMainLooper())
    private var nextName: String? = null
    private var nextTs: Long = 0L

    private val tickRunnable = object : Runnable {
        override fun run() {
            updateCountdown()
            ticker.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        repo = Repo(this)

        b.rvToday.layoutManager = LinearLayoutManager(this)
        b.rvToday.adapter = todayAdapter
        b.rvMonth.layoutManager = LinearLayoutManager(this)
        b.rvMonth.adapter = monthAdapter

        b.tabToday.setOnClickListener { showTab(today = true) }
        b.tabMonth.setOnClickListener { showTab(today = false) }

        // Long-press the countdown card to preview the prayer notification + sound.
        b.hero.setOnLongClickListener {
            Notifier.showPrayer(this, nextName ?: "", b.tvNextTime.text.toString())
            Toast.makeText(this, "Notification sent", Toast.LENGTH_SHORT).show()
            true
        }

        requestNotificationPermissionIfNeeded()
        loadEverything()
    }

    override fun onResume() {
        super.onResume()
        ticker.post(tickRunnable)
        // Keep the home-screen widget in sync with the latest cached data.
        Thread { PrayerWidget.updateAll(applicationContext) }.start()
    }

    override fun onPause() {
        super.onPause()
        ticker.removeCallbacks(tickRunnable)
    }

    private fun loadEverything() {
        repo.loadToday(
            ok = { (day, stale) ->
                b.tvError.visibility = View.GONE
                b.tvDate.text = DateFmt.header(TimeUtils.todayKey())
                b.tvStale.visibility = if (stale) View.VISIBLE else View.GONE

                val now = TimeUtils.nowSeconds()
                val currentKey = PrayerService.currentPrayerKey(day, now)
                val rows = PrayerService.PRAYER_ORDER.mapNotNull { key ->
                    day[key]?.let {
                        TodayAdapter.Row(Labels.display(key), it.time, key == currentKey, iconFor(key))
                    }
                }
                todayAdapter.submit(rows)
            },
            err = {
                if (todayAdapter.itemCount == 0) b.tvError.visibility = View.VISIBLE
            },
        )

        repo.nextPrayer(
            ok = { next ->
                nextName = Labels.display(next.name)
                nextTs = next.prayer.timestamp
                b.tvNextName.text = nextName
                b.tvNextTime.text = next.prayer.time
                b.ivNextIcon.setImageResource(iconFor(next.name))
                b.ivNextIcon.setColorFilter(ContextCompat.getColor(this, R.color.accent))
                updateCountdown()
                // Re-arm the alarm chain off the main thread.
                Thread { PrayerScheduler.scheduleNext(applicationContext) }.start()
                maybePromptBatteryOptimization()
            },
            err = { },
        )

        repo.loadMonth(
            ok = { month ->
                val todayKey = TimeUtils.todayKey()
                val rows = month.map { (key, day) ->
                    val cells = PrayerService.PRAYER_ORDER.mapNotNull { k ->
                        day[k]?.let { MonthAdapter.Cell(iconFor(k), Labels.display(k), it.time) }
                    }
                    MonthAdapter.DayRow(DateFmt.short(key), cells, isToday = key == todayKey)
                }
                monthAdapter.submit(rows)
                if (b.rvMonth.visibility == View.VISIBLE) centerTodayInMonth()
            },
            err = { },
        )
    }

    private fun updateCountdown() {
        if (nextTs <= 0L) return
        val rem = nextTs - TimeUtils.nowSeconds()
        if (rem <= 0L) {
            // Prayer just arrived — pause ticking (nextTs=0) until the async
            // refresh sets the following prayer, then refresh.
            nextTs = 0L
            loadEverything()
            return
        }
        val h = rem / 3600
        val m = (rem % 3600) / 60
        val s = rem % 60
        b.tvCountdown.text = "%02d:%02d:%02d".format(h, m, s)
    }

    private fun showTab(today: Boolean) {
        b.rvToday.visibility = if (today) View.VISIBLE else View.GONE
        b.hero.visibility = if (today) View.VISIBLE else View.GONE
        b.rvMonth.visibility = if (today) View.GONE else View.VISIBLE
        if (!today) centerTodayInMonth()
        b.tabToday.setBackgroundResource(
            if (today) R.drawable.tab_selected else R.drawable.tab_unselected,
        )
        b.tabMonth.setBackgroundResource(
            if (today) R.drawable.tab_unselected else R.drawable.tab_selected,
        )
        val onColor = ContextCompat.getColor(this, R.color.text_primary)
        val offColor = ContextCompat.getColor(this, R.color.text_secondary)
        b.tvTabToday.setTextColor(if (today) onColor else offColor)
        b.tvTabMonth.setTextColor(if (today) offColor else onColor)
        b.ivTabToday.setColorFilter(if (today) onColor else offColor)
        b.ivTabMonth.setColorFilter(if (today) offColor else onColor)
    }

    private fun iconFor(key: String): Int = PrayerIcons.of(key)

    /** Scroll the month list so today's card sits in the middle of the screen.
     *  rvMonth is fully expanded inside the NestedScrollView, so we scroll the
     *  NestedScrollView (not the RecyclerView) to today's card position. */
    private fun centerTodayInMonth() {
        val pos = monthAdapter.indexOfToday()
        if (pos < 0) return
        // Delay so rvMonth has laid out its children AND the NestedScrollView has
        // recomputed its scroll range after rvMonth became visible.
        b.scroll.postDelayed({
            val child = b.rvMonth.layoutManager?.findViewByPosition(pos)
                ?: b.rvMonth.getChildAt(pos) ?: return@postDelayed
            val cardTop = b.rvMonth.top + child.top
            val target = cardTop - (b.scroll.height / 2 - child.height / 2)
            b.scroll.smoothScrollTo(0, target.coerceAtLeast(0))
        }, 200)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }
    }

    /** The key reliability step on Samsung: exclude from battery optimization. */
    private fun maybePromptBatteryOptimization() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val pm = getSystemService(PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(packageName)) return

        val prefs = getSharedPreferences("namozvaqti", MODE_PRIVATE)
        if (prefs.getBoolean("asked_batt", false)) return
        prefs.edit().putBoolean("asked_batt", true).apply()

        AlertDialog.Builder(this, R.style.Theme_NamozVaqti_Dialog)
            .setTitle(R.string.reliability_title)
            .setMessage(R.string.reliability_msg)
            .setPositiveButton(R.string.reliability_ok) { _, _ ->
                try {
                    startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:$packageName"),
                        ),
                    )
                } catch (_: Exception) {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }
            .setNegativeButton(R.string.reliability_later, null)
            .show()
    }
}
