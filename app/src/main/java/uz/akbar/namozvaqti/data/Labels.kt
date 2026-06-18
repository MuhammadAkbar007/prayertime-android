package uz.akbar.namozvaqti.data

/** English display names for the internal prayer keys (Aladhan naming). */
object Labels {
    private val NAMES = mapOf(
        "fajr" to "Fajr",
        "sunrise" to "Sunrise",
        "dhuhr" to "Dhuhr",
        "asr" to "Asr",
        "maghrib" to "Maghrib",
        "isha" to "Isha",
    )

    fun display(key: String): String =
        NAMES[key] ?: key.replaceFirstChar { it.uppercase() }
}
