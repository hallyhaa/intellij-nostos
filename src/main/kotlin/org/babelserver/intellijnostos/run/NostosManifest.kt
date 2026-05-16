package org.babelserver.intellijnostos.run

/**
 * A `[[bin]]` entry point declared in a project's nostos.toml.
 *
 * @param name the binary name, used with the `--bin` flag
 * @param entry the entry point as `module.function` (may be empty if omitted)
 * @param isDefault whether this is the project's default entry point
 */
data class NostosBin(
    val name: String,
    val entry: String,
    val isDefault: Boolean,
)

/**
 * A minimal, dependency-free reader for the `[[bin]]` tables of a nostos.toml.
 *
 * It deliberately understands only what the plugin needs — array-of-tables
 * named `bin` with string `name`/`entry` and boolean `default` keys — and
 * ignores everything else. Parsing is pure so it can be unit-tested.
 */
object NostosManifest {

    /** Parses every `[[bin]]` entry from the given nostos.toml content. */
    fun parseBins(tomlContent: String): List<NostosBin> {
        val tables = mutableListOf<MutableMap<String, String>>()
        var current: MutableMap<String, String>? = null

        for (rawLine in tomlContent.lineSequence()) {
            val line = stripComment(rawLine).trim()
            if (line.isEmpty()) continue

            if (line.startsWith("[")) {
                val isArrayOfTables = line.startsWith("[[") && line.endsWith("]]")
                val tableName = line.trim('[', ']').trim()
                current = if (isArrayOfTables && tableName == "bin") {
                    mutableMapOf<String, String>().also { tables.add(it) }
                } else {
                    null
                }
                continue
            }

            val table = current ?: continue
            val separator = line.indexOf('=')
            if (separator < 0) continue
            val key = line.substring(0, separator).trim()
            val value = line.substring(separator + 1).trim()
            if (key.isNotEmpty()) table[key] = value
        }

        return tables.mapNotNull { table ->
            val name = unquote(table["name"]) ?: return@mapNotNull null
            if (name.isEmpty()) return@mapNotNull null
            NostosBin(
                name = name,
                entry = unquote(table["entry"]) ?: "",
                isDefault = table["default"]?.trim() == "true",
            )
        }
    }

    /** Drops a trailing `#` comment, leaving `#` characters inside strings intact. */
    private fun stripComment(line: String): String {
        var inString = false
        for (i in line.indices) {
            when (line[i]) {
                '"' -> inString = !inString
                '#' -> if (!inString) return line.substring(0, i)
            }
        }
        return line
    }

    /** Returns the content of a quoted TOML string, or null if [value] is not quoted. */
    private fun unquote(value: String?): String? {
        val trimmed = value?.trim() ?: return null
        if (trimmed.length < 2) return null
        val quote = trimmed.first()
        if ((quote == '"' || quote == '\'') && trimmed.last() == quote) {
            return trimmed.substring(1, trimmed.length - 1)
        }
        return null
    }
}
