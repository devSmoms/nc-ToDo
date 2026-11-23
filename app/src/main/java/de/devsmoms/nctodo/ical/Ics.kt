package de.devsmoms.nctodo.ical

/**
 * Minimal, lossless iCalendar (RFC 5545) model.
 *
 * Unknown properties and components are preserved verbatim so that editing a task
 * in this app never destroys data written by other clients (e.g. alarms, recurrence).
 */
data class IcsParam(val name: String, val value: String)

class IcsProperty(
    var name: String,
    val params: MutableList<IcsParam> = mutableListOf(),
    /** Raw (still escaped) value as it appears in the file. */
    var rawValue: String,
) {
    fun param(name: String): String? =
        params.firstOrNull { it.name.equals(name, ignoreCase = true) }?.value?.let(::unquote)

    fun setParam(name: String, value: String?) {
        params.removeAll { it.name.equals(name, ignoreCase = true) }
        if (value != null) params.add(IcsParam(name.uppercase(), quoteIfNeeded(value)))
    }

    /** Value interpreted as TEXT (escape sequences resolved). */
    val text: String get() = Ics.unescapeText(rawValue)

    private companion object {
        fun unquote(v: String) = if (v.length >= 2 && v.startsWith('"') && v.endsWith('"')) v.substring(1, v.length - 1) else v
        fun quoteIfNeeded(v: String): String {
            // Double quotes are not allowed inside parameter values at all.
            val clean = v.replace("\"", "")
            return if (clean.any { it == ':' || it == ';' || it == ',' }) "\"$clean\"" else clean
        }
    }
}

class IcsComponent(val name: String) {
    val properties: MutableList<IcsProperty> = mutableListOf()
    val components: MutableList<IcsComponent> = mutableListOf()

    fun prop(name: String): IcsProperty? = properties.firstOrNull { it.name.equals(name, ignoreCase = true) }
    fun props(name: String): List<IcsProperty> = properties.filter { it.name.equals(name, ignoreCase = true) }

    fun removeProp(name: String) {
        properties.removeAll { it.name.equals(name, ignoreCase = true) }
    }

    /** Replaces all occurrences of [name] with a single property, keeping its position if it existed. */
    fun setProp(name: String, rawValue: String, params: List<IcsParam> = emptyList()): IcsProperty {
        val upper = name.uppercase()
        val idx = properties.indexOfFirst { it.name.equals(upper, ignoreCase = true) }
        removeProp(upper)
        val p = IcsProperty(upper, params.toMutableList(), rawValue)
        if (idx in 0..properties.size) properties.add(idx, p) else properties.add(p)
        return p
    }

    fun setText(name: String, value: String) = setProp(name, Ics.escapeText(value))

    fun component(name: String): IcsComponent? = components.firstOrNull { it.name.equals(name, ignoreCase = true) }
}

class IcsParseException(message: String) : Exception(message)

object Ics {
    private const val MAX_INPUT_CHARS = 2_000_000
    private const val MAX_DEPTH = 16

    fun parse(input: String): IcsComponent {
        if (input.length > MAX_INPUT_CHARS) throw IcsParseException("iCalendar data too large")
        val lines = unfold(input)
        val stack = ArrayDeque<IcsComponent>()
        var root: IcsComponent? = null
        for (line in lines) {
            if (line.isBlank()) continue
            val prop = parseLine(line) ?: continue
            when (prop.name) {
                "BEGIN" -> {
                    if (stack.size >= MAX_DEPTH) throw IcsParseException("Nesting too deep")
                    val c = IcsComponent(prop.rawValue.trim().uppercase())
                    stack.lastOrNull()?.components?.add(c)
                    if (stack.isEmpty()) {
                        if (root != null) throw IcsParseException("Multiple root components")
                        root = c
                    }
                    stack.addLast(c)
                }
                "END" -> {
                    val c = stack.removeLastOrNull() ?: throw IcsParseException("Unexpected END")
                    if (!c.name.equals(prop.rawValue.trim(), ignoreCase = true)) {
                        throw IcsParseException("END:${prop.rawValue} does not match BEGIN:${c.name}")
                    }
                }
                else -> stack.lastOrNull()?.properties?.add(prop)
                    ?: throw IcsParseException("Property outside of component")
            }
        }
        if (stack.isNotEmpty()) throw IcsParseException("Unterminated component ${stack.last().name}")
        return root ?: throw IcsParseException("No component found")
    }

    fun serialize(component: IcsComponent): String {
        val sb = StringBuilder()
        write(component, sb)
        return sb.toString()
    }

    private fun write(c: IcsComponent, sb: StringBuilder) {
        appendFolded(sb, "BEGIN:${c.name}")
        for (p in c.properties) {
            val line = buildString {
                append(p.name)
                for (param in p.params) append(';').append(param.name).append('=').append(param.value)
                append(':').append(stripLineBreaks(p.rawValue))
            }
            appendFolded(sb, line)
        }
        for (child in c.components) write(child, sb)
        appendFolded(sb, "END:${c.name}")
    }

    /** Raw values must never contain real line breaks; that would allow property injection. */
    private fun stripLineBreaks(v: String) = v.replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "")

    /** Folds lines at 75 octets (UTF-8) without splitting multi-byte characters. */
    private fun appendFolded(sb: StringBuilder, line: String) {
        var octets = 0
        var limit = 75
        var i = 0
        while (i < line.length) {
            val cp = line.codePointAt(i)
            val len = Character.charCount(cp)
            val bytes = String(Character.toChars(cp)).toByteArray(Charsets.UTF_8).size
            if (octets + bytes > limit) {
                sb.append("\r\n ")
                octets = 1 // the leading space counts
                limit = 75
            }
            sb.appendCodePoint(cp)
            octets += bytes
            i += len
        }
        sb.append("\r\n")
    }

    private fun unfold(input: String): List<String> {
        val normalized = input.replace("\r\n", "\n").replace('\r', '\n')
        val out = ArrayList<String>()
        for (raw in normalized.split('\n')) {
            if ((raw.startsWith(" ") || raw.startsWith("\t")) && out.isNotEmpty()) {
                out[out.size - 1] = out.last() + raw.substring(1)
            } else {
                out.add(raw)
            }
        }
        return out
    }

    /** Parses `NAME;P1=V1;P2="a:b":VALUE`. Returns null for malformed lines (they are dropped). */
    internal fun parseLine(line: String): IcsProperty? {
        var i = 0
        val n = line.length
        while (i < n && line[i] != ';' && line[i] != ':') i++
        if (i == 0 || i >= n) return null
        val name = line.substring(0, i).uppercase()
        val params = mutableListOf<IcsParam>()
        while (i < n && line[i] == ';') {
            i++
            val eq = line.indexOf('=', i)
            if (eq < 0) return null
            val pName = line.substring(i, eq).uppercase()
            i = eq + 1
            val start = i
            var inQuotes = false
            while (i < n) {
                val ch = line[i]
                if (ch == '"') inQuotes = !inQuotes
                else if (!inQuotes && (ch == ';' || ch == ':')) break
                i++
            }
            params.add(IcsParam(pName, line.substring(start, i)))
        }
        if (i >= n || line[i] != ':') return null
        return IcsProperty(name, params, line.substring(i + 1))
    }

    fun escapeText(v: String): String = buildString {
        for (ch in v) when (ch) {
            '\\' -> append("\\\\")
            ';' -> append("\\;")
            ',' -> append("\\,")
            '\n' -> append("\\n")
            '\r' -> {}
            else -> append(ch)
        }
    }

    fun unescapeText(v: String): String {
        val sb = StringBuilder(v.length)
        var i = 0
        while (i < v.length) {
            val ch = v[i]
            if (ch == '\\' && i + 1 < v.length) {
                when (val next = v[i + 1]) {
                    'n', 'N' -> sb.append('\n')
                    '\\', ';', ',' -> sb.append(next)
                    else -> sb.append(ch).append(next)
                }
                i += 2
            } else {
                sb.append(ch); i++
            }
        }
        return sb.toString()
    }
}
