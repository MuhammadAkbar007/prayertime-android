package ai.wakil.namozvaqti.data

/** One prayer: display time string + its epoch-second timestamp (Tashkent clock). */
data class Prayer(val time: String, val timestamp: Long)

/** One day, name -> Prayer. LinkedHashMap to keep a stable, ordered layout. */
typealias Day = LinkedHashMap<String, Prayer>
