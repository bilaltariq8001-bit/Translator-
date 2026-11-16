package com.translator.app.core.analytics

/**
 * Builds the parameters of one analytics event and keeps anything personal out of them.
 *
 * Only these shapes can get in: a short token (letters, digits, underscore, dot, dash, plus; at most [MAX_VALUE_LENGTH]
 * characters, so no spaces and therefore no sentences, no paths and no web addresses), a language code, a number, a yes or
 * no, a bucket or a duration. A value that does not fit is dropped and counted in [rejected]; nothing ever throws, so a mistake
 * in a call cannot crash the app. Typed text, audio, images and file paths are never passed to this class.
 */
class EventBuilder {
    private val values = LinkedHashMap<String, Any>()

    /** How many values or keys were refused. Zero for a correct call. */
    var rejected = 0
        private set

    /** A short fixed word such as a screen name, a mode or a result. Free text is refused. */
    fun token(key: String, value: String): EventBuilder = put(key, value.takeIf { TOKEN.matches(it) })

    /** A language code such as "en" or "zh-TW". */
    fun lang(key: String, code: String): EventBuilder = put(key, code.takeIf { LANG.matches(it) })

    fun number(key: String, value: Long): EventBuilder = put(key, value)

    fun flag(key: String, value: Boolean): EventBuilder = put(key, if (value) 1L else 0L)

    /** A coarse size, for example the length of a text: "0", "lt_1", "1_9" ... never the exact value. */
    fun bucket(key: String, value: Long, edges: List<Long> = DEFAULT_EDGES): EventBuilder = put(key, bucketOf(value, edges))

    /** A duration in whole seconds, bucketed. */
    fun duration(key: String, millis: Long): EventBuilder = put(key, bucketOf(millis / 1000, DURATION_EDGES))

    /** A code from a library (an error code). Lowercased and cut to a safe token; use it only for such codes, never for text. */
    fun code(key: String, raw: String): EventBuilder {
        val cleaned = raw.lowercase().replace(Regex("[^a-z0-9_.+-]"), "_").take(MAX_VALUE_LENGTH)
        return put(key, cleaned.takeIf { it.isNotEmpty() })
    }

    private fun put(key: String, value: Any?): EventBuilder {
        if (value == null || !KEY.matches(key) || (!values.containsKey(key) && values.size >= MAX_PARAMS)) { rejected++; return this }
        values[key] = value
        return this
    }

    fun build(): Map<String, Any> = LinkedHashMap(values)

    companion object {
        const val MAX_VALUE_LENGTH = 40
        const val MAX_PARAMS = 25
        private val TOKEN = Regex("[A-Za-z0-9_.+-]{1,40}")
        private val LANG = Regex("[a-z]{2,3}(-[A-Za-z0-9]{2,8})?")
        private val KEY = Regex("[a-z][a-z0-9_]{0,39}")
        val DEFAULT_EDGES = listOf(1L, 10L, 50L, 200L, 1000L)
        val DURATION_EDGES = listOf(1L, 3L, 10L, 30L, 120L)

        fun bucketOf(value: Long, edges: List<Long>): String {
            if (value <= 0) return "0"
            var low = 0L
            for (edge in edges) {
                if (value < edge) return if (low == 0L) "lt_$edge" else "${low}_${edge - 1}"
                low = edge
            }
            return "${edges.last()}_plus"
        }
    }
}
