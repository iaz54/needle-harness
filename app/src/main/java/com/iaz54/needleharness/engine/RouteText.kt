package com.iaz54.needleharness.engine

data class RoutePlan(
    val origin: String,
    val destination: String,
    val waypoints: List<String>,
    val mode: String,
    val avoid: List<String>,
)

object RouteText {
    private val ALIASES = mapOf(
        "home" to "Home",
        "house" to "Home",
        "my house" to "Home",
        "my home" to "Home",
        "work" to "Work",
        "the office" to "Work",
        "my office" to "Work",
        "airport" to "Airport",
        "the airport" to "Airport",
        "downtown" to "Downtown",
        "grocery" to "grocery store",
        "the grocery" to "grocery store",
        "grocery store" to "grocery store",
        "the grocery store" to "grocery store",
        "gas" to "gas station",
        "gas station" to "gas station",
        "the gas station" to "gas station",
    )

    private val STOP_NAV = Regex(
        """\b(?:stop|cancel|end|quit)\s+(?:the\s+)?(?:nav|navigation|route|directions|trip)\b|\bstop navigating\b""",
        RegexOption.IGNORE_CASE,
    )
    private val COMMAND_START = Regex(
        """^(?:turn|switch|set|lock|unlock|dim|brighten|darken|open|launch|close|call|dial|phone|text|sms|message|email|e-mail|search|google|look|find|navigate|take|drive|walk|bike|play|pause|skip|resume|start|stop|enable|disable|mute|unmute|copy|add|create|schedule|flash|toggle|weather|forecast|calculate|compute|note|remember|memo)\b""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(raw: String): RoutePlan? {
        if (STOP_NAV.containsMatchIn(raw) && !Regex("""\b(?:from|via|to)\b""", RegexOption.IGNORE_CASE).containsMatchIn(raw)) {
            return null
        }
        val (avoid, rest0) = parseAvoid(raw)
        val mode = parseMode(rest0)
        val rest = rest0

        val fromTo = Regex("""\bfrom\s+(.+?)\s+to\s+(.+)$""", RegexOption.IGNORE_CASE).find(rest)
        if (fromTo != null) {
            val origin = hereOrigin(fromTo.groupValues[1])
            val tail = fromTo.groupValues[2]
            val via = Regex(
                """^(.+?)\s+(?:via|through|stopping at|stops at|with (?:a )?stops? at)\s+(.+)$""",
                RegexOption.IGNORE_CASE,
            ).find(tail)
            if (via != null) {
                val destination = aliasPlace(via.groupValues[1])
                if (destination.length >= 2) {
                    return RoutePlan(origin, destination, splitPlaces(via.groupValues[2]).take(9), mode, avoid)
                }
            }
            val ordered = splitPlaces(tail)
            if (ordered.size >= 2) {
                return RoutePlan(origin, ordered.last(), ordered.dropLast(1).take(9), mode, avoid)
            }
            if (ordered.size == 1) return RoutePlan(origin, ordered[0], emptyList(), mode, avoid)
        }

        val onTheWay = Regex(
            """\bstop(?:ping)?\s+at\s+(.+?)\s+on the way to\s+(.+)$""",
            RegexOption.IGNORE_CASE,
        ).find(rest)
        if (onTheWay != null) {
            val destination = aliasPlace(onTheWay.groupValues[2])
            if (destination.length >= 2) {
                return RoutePlan("", destination, splitPlaces(onTheWay.groupValues[1]).take(9), mode, avoid)
            }
        }

        val toVia = Regex(
            """\bto\s+(.+?)\s+(?:via|through|stopping at|with (?:a )?stops? at)\s+(.+)$""",
            RegexOption.IGNORE_CASE,
        ).find(rest)
        if (toVia != null) {
            val destination = aliasPlace(toVia.groupValues[1])
            if (destination.length >= 2) {
                return RoutePlan("", destination, splitPlaces(toVia.groupValues[2]).take(9), mode, avoid)
            }
        }

        val arrows = rest.split(Regex("""\s*(?:->|→)\s*"""))
        if (arrows.size >= 2) {
            val places = arrows.map { aliasPlace(it) }.filter { it.length >= 2 }
            if (places.size >= 2) {
                return RoutePlan(places.first(), places.last(), places.drop(1).dropLast(1).take(9), mode, avoid)
            }
        }

        val listed = Regex("""\b(?:route|trip|itinerary)\b\s*[:\-]?\s+(.+)$""", RegexOption.IGNORE_CASE).find(rest)
        if (listed != null && Regex(""",| then | and |->|→""").containsMatchIn(listed.groupValues[1])) {
            val places = splitPlaces(listed.groupValues[1])
            if (places.size >= 2) {
                return RoutePlan(places.first(), places.last(), places.drop(1).dropLast(1).take(9), mode, avoid)
            }
        }

        val start = Regex(
            """(?:navigate(?:\s+me)?(?:\s+to)?|start(?:ing)?\s+nav(?:igation)?(?:\s+to)?|take me(?:\s+to)?|directions(?:\s+to)?|route(?:\s+me)?(?:\s+to)?|(?:drive|walk|bike)(?:\s+me)?\s+to|nav\s+to)\s+(.+)""",
            RegexOption.IGNORE_CASE,
        ).find(rest) ?: return null
        if (STOP_NAV.containsMatchIn(rest)) return null
        val places = splitPlaces(start.groupValues[1])
        if (places.size >= 2) {
            return RoutePlan("", places.last(), places.dropLast(1).take(9), mode, avoid)
        }
        val destination = places.firstOrNull() ?: return null
        if (destination.length < 2) return null
        return RoutePlan("", destination, emptyList(), mode, avoid)
    }

    fun split(text: String): List<String> {
        val atoms = text.split(Regex("""\s*(?:;|\band then\b|,|\band\b|\bthen\b)\s*""", RegexOption.IGNORE_CASE))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val merged = mutableListOf<String>()
        for (atom in atoms) {
            val prev = merged.lastOrNull()
            if (prev == null || COMMAND_START.containsMatchIn(atom)) {
                merged += atom
                continue
            }
            val prevIsRoute = Regex("""\bfrom\s+.+\sto\s+""", RegexOption.IGNORE_CASE).containsMatchIn(prev) ||
                Regex("""\b(?:via|through|stopping at|stops at)\b""", RegexOption.IGNORE_CASE).containsMatchIn(prev) ||
                Regex("""^(?:route|itinerary|trip|plan)\b""", RegexOption.IGNORE_CASE).containsMatchIn(prev) ||
                Regex(
                    """\b(?:navigate|directions|take me|(?:drive|walk|bike)(?:\s+me)?\s+to|nav\s+to)\b""",
                    RegexOption.IGNORE_CASE,
                ).containsMatchIn(prev)
            if (prevIsRoute) merged[merged.lastIndex] = "$prev and $atom" else merged += atom
        }
        if (merged.size >= 2 && merged.all { !COMMAND_START.containsMatchIn(it) }) {
            val asRoute = "route ${merged.joinToString(", ")}"
            if (parse(asRoute) != null) return listOf(asRoute)
        }
        return merged.ifEmpty { listOf(text.trim()).filter { it.isNotEmpty() } }
    }

    private fun parseMode(text: String): String {
        if (Regex("""\b(?:walk|walking|on foot|by foot)\b""", RegexOption.IGNORE_CASE).containsMatchIn(text)) return "walking"
        if (Regex(
                """\b(?:by\s+transit|public transit|by\s+subway|by\s+train|by\s+bus|take\s+(?:the\s+)?(?:subway|train|bus)|transit to|transit from)\b""",
                RegexOption.IGNORE_CASE,
            ).containsMatchIn(text)
        ) return "transit"
        if (Regex("""\b(?:by\s+bike|biking|bicycling|bicycle|bike to|bike from|bike me)\b""", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            return "bicycling"
        }
        return "driving"
    }

    private fun parseAvoid(text: String): Pair<List<String>, String> {
        val avoid = linkedSetOf<String>()
        var rest = text
        val rules = listOf(
            Regex("""\b(?:avoid(?:ing)?|no|without)\s+tolls?\b""", RegexOption.IGNORE_CASE) to "tolls",
            Regex("""\b(?:avoid(?:ing)?|no|without)\s+(?:the\s+)?(?:highways?|motorways?|freeways?)\b""", RegexOption.IGNORE_CASE) to "highways",
            Regex("""\b(?:avoid(?:ing)?|no|without)\s+ferries\b""", RegexOption.IGNORE_CASE) to "ferries",
        )
        for ((re, flag) in rules) {
            if (re.containsMatchIn(rest)) {
                avoid += flag
                rest = re.replace(rest, " ")
            }
        }
        rest = rest.replace(Regex("""\s+"""), " ").trim().trim(',', ' ')
        return avoid.toList() to rest
    }

    private fun aliasPlace(raw: String): String {
        val c = raw.replace(Regex("""\s+(?:please|now|today)$""", RegexOption.IGNORE_CASE), "")
            .trimEnd('.', '?', '!', ',')
            .replace(Regex("""\s+"""), " ")
            .trim()
        val key = c.lowercase().replace(Regex("""^(?:a|an)\s+"""), "")
        return ALIASES[key] ?: ALIASES[c.lowercase()] ?: c
    }

    private fun hereOrigin(raw: String): String {
        val c = aliasPlace(raw.replace(Regex("""^(?:a\s+)?(?:drive|walk|bike|trip|route|transit)\s+""", RegexOption.IGNORE_CASE), ""))
        if (Regex("""^(?:here|current location|my location|where i am)$""", RegexOption.IGNORE_CASE).matches(c)) return ""
        return c
    }

    private fun splitPlaces(s: String): List<String> {
        return s.split(Regex("""\s*(?:,|&|\+|\band then\b|\band\b|\bthen\b|\bplus\b|->|→)\s*""", RegexOption.IGNORE_CASE))
            .map { it.replace(Regex("""^(?:(?:at|to|via|through|by|then|stop(?:ping)?(?:\s+at)?)\s+)+""", RegexOption.IGNORE_CASE), "").trim() }
            .map { aliasPlace(it) }
            .filter { it.length >= 2 }
    }
}
