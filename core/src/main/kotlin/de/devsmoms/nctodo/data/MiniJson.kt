package de.devsmoms.nctodo.data

/**
 * Minimal JSON reader for the few small server answers NC Todo reads (Login Flow v2).
 * Objects become Map<String, Any?>, arrays List<Any?>, numbers Double, plus String/Boolean/null.
 * Kept dependency-free so the shared code runs on Android and desktop alike.
 */
object MiniJson {

    /** Parses [text] as a JSON object; null if it is not valid JSON or not an object. */
    fun parseObject(text: String): Map<String, Any?>? = runCatching {
        val p = Parser(text)
        val value = p.value()
        p.end()
        @Suppress("UNCHECKED_CAST")
        value as? Map<String, Any?>
    }.getOrNull()

    /** String value of [key], or "" if missing or not a string. */
    fun string(obj: Map<*, *>, key: String): String = obj[key] as? String ?: ""

    private class Parser(private val s: String) {
        private var i = 0

        fun end() {
            ws()
            require(i == s.length) { "Trailing data" }
        }

        fun value(): Any? {
            ws()
            require(i < s.length) { "Unexpected end" }
            return when (s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> num()
            }
        }

        private fun obj(): Map<String, Any?> {
            val map = LinkedHashMap<String, Any?>()
            i++ // {
            ws()
            if (peek() == '}') { i++; return map }
            while (true) {
                ws()
                val key = str()
                ws(); expect(':')
                map[key] = value()
                ws()
                when (next()) {
                    ',' -> continue
                    '}' -> return map
                    else -> throw IllegalArgumentException("Expected , or }")
                }
            }
        }

        private fun arr(): List<Any?> {
            val list = ArrayList<Any?>()
            i++ // [
            ws()
            if (peek() == ']') { i++; return list }
            while (true) {
                list += value()
                ws()
                when (next()) {
                    ',' -> continue
                    ']' -> return list
                    else -> throw IllegalArgumentException("Expected , or ]")
                }
            }
        }

        private fun str(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                val c = next()
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> when (val e = next()) {
                        '"', '\\', '/' -> sb.append(e)
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            require(i + 4 <= s.length) { "Bad escape" }
                            sb.append(s.substring(i, i + 4).toInt(16).toChar())
                            i += 4
                        }
                        else -> throw IllegalArgumentException("Bad escape")
                    }
                    else -> {
                        require(c >= ' ') { "Control character in string" }
                        sb.append(c)
                    }
                }
            }
        }

        private fun num(): Double {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            return s.substring(start, i).toDouble()
        }

        private fun literal(word: String, v: Any?): Any? {
            require(s.startsWith(word, i)) { "Unexpected token" }
            i += word.length
            return v
        }

        private fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        private fun peek(): Char? = s.getOrNull(i)
        private fun next(): Char { require(i < s.length) { "Unexpected end" }; return s[i++] }
        private fun expect(c: Char) { require(next() == c) { "Expected $c" } }
    }
}
