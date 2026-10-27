package com.example.mockgpslab.data

data class SavedPlace(val name: String, val point: GeoPoint)

/** Built-in list. Patna is the project's home base; the others are handy for checking big jumps. */
object Presets {
    val builtIn = listOf(
        SavedPlace("Patna", GeoPoint.PATNA),
        SavedPlace("New Delhi", GeoPoint(28.6139, 77.2090)),
        SavedPlace("Mumbai", GeoPoint(19.0760, 72.8777)),
        SavedPlace("Bengaluru", GeoPoint(12.9716, 77.5946)),
    )
}

/**
 * One place per line: "lat|lon|name". The name goes last and is split with limit = 3, so it may contain '|'.
 * A single DataStore string is plenty for a hand-managed list and keeps this free of JSON libraries.
 */
object SavedPlacesCodec {
    fun encode(places: List<SavedPlace>): String =
        places.joinToString("\n") { "${it.point.latitude}|${it.point.longitude}|${clean(it.name)}" }

    /** Malformed lines are skipped rather than failing the whole list. */
    fun decode(raw: String?): List<SavedPlace> =
        raw.orEmpty().lineSequence().mapNotNull { line ->
            val parts = line.split('|', limit = 3)
            if (parts.size != 3) return@mapNotNull null
            val lat = Coordinates.parseLatitude(parts[0]).valueOrNull ?: return@mapNotNull null
            val lon = Coordinates.parseLongitude(parts[1]).valueOrNull ?: return@mapNotNull null
            val name = parts[2].trim().ifEmpty { return@mapNotNull null }
            SavedPlace(name, GeoPoint(lat, lon))
        }.toList()

    // A newline in a name would split one place into two lines.
    private fun clean(name: String) = name.replace(Regex("\\s+"), " ").trim()
}
