package uz.akbar.namozvaqti.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.Executors

/**
 * Tiny background-work helper so network/disk never touch the main thread.
 * No coroutines on purpose — keeps the APK lean for an old device.
 */
class Repo(context: Context) {
    private val service = PrayerService(Cache(context.applicationContext))
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    // Catch Throwable (not just Exception) so a stray Error on the worker
    // thread surfaces as the on-screen message instead of crashing the app.
    private fun <T> run(work: () -> T, ok: (T) -> Unit, err: (Throwable) -> Unit) {
        io.execute {
            try {
                val r = work()
                main.post { ok(r) }
            } catch (t: Throwable) {
                Log.e("NamozVaqti", "background work failed", t)
                main.post { err(t) }
            }
        }
    }

    fun loadToday(ok: (Pair<Day, Boolean>) -> Unit, err: (Throwable) -> Unit) =
        run({ service.getTodayResilient() }, ok, err)

    fun loadMonth(ok: (LinkedHashMap<String, Day>) -> Unit, err: (Throwable) -> Unit) =
        run({ service.getMonth() }, ok, err)

    fun nextPrayer(ok: (PrayerService.Next) -> Unit, err: (Throwable) -> Unit) =
        run({ service.getNextPrayerResilient() }, ok, err)
}
