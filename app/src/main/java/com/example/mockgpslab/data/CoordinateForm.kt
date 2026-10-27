package com.example.mockgpslab.data

import java.util.Locale

/**
 * The text the user has typed into the latitude / longitude / accuracy fields, plus everything derived from it.
 * Immutable and pure, so the "paste a pair into one field" behaviour can be unit tested without a UI.
 */
data class CoordinateForm(
    val latText: String = "",
    val lonText: String = "",
    val accuracyText: String = format(DEFAULT_ACCURACY),
) {
    val lat: Parsed<Double> get() = Coordinates.parseLatitude(latText)
    val lon: Parsed<Double> get() = Coordinates.parseLongitude(lonText)
    val accuracy: Parsed<Float> get() = parseAccuracy(accuracyText)

    /** The selected point, or null while either field is invalid. */
    val point: GeoPoint?
        get() {
            val la = lat.valueOrNull ?: return null
            val lo = lon.valueOrNull ?: return null
            return GeoPoint(la, lo)
        }

    val accuracyMeters: Float? get() = accuracy.valueOrNull

    /** Pasting "25.5941, 85.1376" into either coordinate field fills both. */
    fun editLatitude(text: String): CoordinateForm = pastedPair(text) ?: copy(latText = text)

    fun editLongitude(text: String): CoordinateForm = pastedPair(text) ?: copy(lonText = text)

    fun editAccuracy(text: String): CoordinateForm = copy(accuracyText = text)

    fun withPoint(point: GeoPoint): CoordinateForm =
        copy(latText = format(point.latitude, 6), lonText = format(point.longitude, 6))

    fun withAccuracy(meters: Float): CoordinateForm = copy(accuracyText = format(meters.toDouble(), 1).removeSuffix(".0"))

    private fun pastedPair(text: String): CoordinateForm? {
        // Cheap pre-check so plain typing ("25.59") never goes through pair parsing.
        if (text.none { it == ',' || it == ';' || it.isWhitespace() }) return null
        return Coordinates.parsePair(text).valueOrNull?.let { withPoint(it) }
    }

    companion object {
        const val DEFAULT_ACCURACY = 5.0
        const val MAX_ACCURACY = 10_000f

        fun parseAccuracy(raw: String): Parsed<Float> {
            val text = raw.trim()
            val value = text.toFloatOrNull()
            return when {
                text.isEmpty() -> Parsed.Invalid("Accuracy is required")
                value == null || !value.isFinite() -> Parsed.Invalid("Accuracy must be a number of metres")
                value <= 0f || value > MAX_ACCURACY -> Parsed.Invalid("Accuracy must be above 0 and at most ${MAX_ACCURACY.toInt()} m")
                else -> Parsed.Ok(value)
            }
        }

        // Locale.US: some locales print a decimal comma, which our parser (rightly) rejects.
        private fun format(value: Double, decimals: Int = 1) = String.format(Locale.US, "%.${decimals}f", value)
    }
}
