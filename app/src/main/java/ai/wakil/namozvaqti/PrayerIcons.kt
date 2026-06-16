package ai.wakil.namozvaqti

/** Maps an internal prayer key (english) to its vector icon. */
object PrayerIcons {
    fun of(key: String): Int = when (key) {
        "fajr" -> R.drawable.ic_prayer_bomdod
        "sunrise" -> R.drawable.ic_prayer_quyosh
        "dhuhr" -> R.drawable.ic_prayer_peshin
        "asr" -> R.drawable.ic_prayer_asr
        "maghrib" -> R.drawable.ic_prayer_shom
        "isha" -> R.drawable.ic_prayer_xufton
        else -> R.drawable.ic_prayer_peshin
    }
}
