package com.example.mockgpslab.data

/** Parse outcome: either a value or a human-readable reason, never an exception. */
sealed interface Parsed<out T> {
    data class Ok<T>(val value: T) : Parsed<T>
    data class Invalid(val message: String) : Parsed<Nothing>
}

val <T> Parsed<T>.valueOrNull: T? get() = (this as? Parsed.Ok)?.value
val Parsed<*>.errorOrNull: String? get() = (this as? Parsed.Invalid)?.message

/** Pure text -> coordinate logic, kept free of Android so it is trivially unit tested. */
object Coordinates {
    // Deliberately stricter than String.toDouble(), which would also accept "NaN", "Infinity", "1e3" and "0x1p3".
    private val NUMBER = Regex("""[+-]?(?:\d+(?:\.\d*)?|\.\d+)""")
    private val PAIR_SEPARATOR = Regex("""\s*[,;]\s*|\s+""")

    fun parseLatitude(raw: String): Parsed<Double> = parseBounded(raw, "Latitude", 90.0)

    fun parseLongitude(raw: String): Parsed<Double> = parseBounded(raw, "Longitude", 180.0)

    /**
     * Accepts what people copy out of maps apps: "25.5941, 85.1376", "25.5941 85.1376", "25.5941;85.1376",
     * optionally wrapped in brackets and with degree signs.
     */
    fun parsePair(raw: String): Parsed<GeoPoint> {
        val tokens = raw.trim().trim('(', ')', '[', ']').split(PAIR_SEPARATOR).filter { it.isNotEmpty() }
        if (tokens.size != 2) return Parsed.Invalid("Expected two numbers like \"25.5941, 85.1376\"")
        val lat = parseLatitude(tokens[0])
        val lon = parseLongitude(tokens[1])
        return when {
            lat is Parsed.Invalid -> lat
            lon is Parsed.Invalid -> lon
            else -> Parsed.Ok(GeoPoint((lat as Parsed.Ok).value, (lon as Parsed.Ok).value))
        }
    }

    private fun parseBounded(raw: String, label: String, limit: Double): Parsed<Double> {
        val text = raw.trim().removeSuffix("°").trim()
        if (text.isEmpty()) return Parsed.Invalid("$label is required")
        if (!NUMBER.matches(text)) return Parsed.Invalid("$label must be a number like 25.5941")
        val value = text.toDouble()
        if (value !in -limit..limit) {
            return Parsed.Invalid("$label must be between -${limit.toInt()} and ${limit.toInt()}")
        }
        return Parsed.Ok(value)
    }
}
