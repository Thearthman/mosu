package com.mosu.app.domain.render

/**
 * Minimal parser for the legacy `skin.ini` format used by osu! skins.
 *
 * Section and key names are case-insensitive. Values are kept verbatim apart
 * from surrounding whitespace. The importer deliberately does not attempt to
 * model every osu! setting; unsupported keys are retained for future callers.
 */
class SkinIni internal constructor(
    private val values: Map<String, Map<String, String>>
) {
    fun value(section: String, key: String): String? =
        values[section.lowercase()]?.get(key.lowercase())

    fun boolean(section: String, key: String, default: Boolean): Boolean {
        val raw = value(section, key)?.trim()?.lowercase() ?: return default
        return when (raw) {
            "1", "true", "yes", "on" -> true
            "0", "false", "no", "off" -> false
            else -> default
        }
    }

    fun colour(key: String): Int? = value("Colours", key)?.let(::parseColour)

    fun comboColours(): List<Int> = values["colours"]
        ?.entries
        ?.filter { it.key.startsWith("combo") }
        ?.sortedBy { it.key.removePrefix("combo").toIntOrNull() ?: Int.MAX_VALUE }
        ?.mapNotNull { parseColour(it.value) }
        ?.distinct()
        .orEmpty()

    companion object {
        fun parse(text: String): SkinIni {
            val values = linkedMapOf<String, MutableMap<String, String>>()
            var section = "general"
            text.removePrefix("\uFEFF").lineSequence().forEach { raw ->
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("//") || line.startsWith(";")) {
                    return@forEach
                }
                if (line.startsWith("[") && line.endsWith("]")) {
                    section = line.substring(1, line.length - 1).trim().lowercase()
                    return@forEach
                }
                val colon = line.indexOf(':')
                val equals = line.indexOf('=')
                val separator = when {
                    colon > 0 && equals > 0 -> minOf(colon, equals)
                    colon > 0 -> colon
                    equals > 0 -> equals
                    else -> -1
                }
                if (separator <= 0) return@forEach
                val key = line.substring(0, separator).trim().lowercase()
                val value = line.substring(separator + 1).trim()
                if (key.isNotEmpty()) {
                    values.getOrPut(section) { linkedMapOf() }[key] = value
                }
            }
            return SkinIni(values)
        }

        fun parseColour(value: String): Int? {
            val components = value.split(',').mapNotNull { it.trim().toIntOrNull() }
            if (components.size < 3 || components.any { it !in 0..255 }) return null
            val alpha = components.getOrNull(3) ?: 255
            return (alpha shl 24) or
                (components[0] shl 16) or
                (components[1] shl 8) or
                components[2]
        }
    }
}
