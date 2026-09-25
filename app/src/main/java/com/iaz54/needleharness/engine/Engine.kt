package com.iaz54.needleharness.engine

data class FunctionCall(val name: String, val arguments: Map<String, Any>)

enum class Gate { EXECUTE, CONFIRM, REFUSE }

data class CompleteResult(
    val type: String,
    val functionCalls: List<FunctionCall>,
    val suppressedCalls: List<FunctionCall>,
    val reasoning: String,
    val confidence: Double?,
    val prefillTps: Int,
    val decodeTps: Int,
    val peakRamMb: Int,
    val latencyMs: Long,
    val layer: Int,
    val gate: Gate,
    val triggered: Boolean,
)

data class ToolSpec(
    val name: String,
    val triggers: List<Regex>,
)

private val ROOMS = mapOf(
    "living" to "living", "living room" to "living", "lounge" to "living",
    "kitchen" to "kitchen", "bedroom" to "bedroom", "bed" to "bedroom",
    "office" to "office", "study" to "office",
    "bathroom" to "bathroom", "bath" to "bathroom",
    "garage" to "garage", "garage door" to "garage",
)

private val APPS = listOf(
    "play store", "spotify", "youtube", "camera", "chrome", "gmail", "messages",
    "calendar", "clock", "photos", "whatsapp", "instagram", "calculator", "files",
    "settings", "phone", "maps",
)

private val TOOLS = listOf(
    ToolSpec("set_lights", listOf(Regex("\\b(lights?|lamp|bulbs?)\\b", RegexOption.IGNORE_CASE), Regex("\\b(dim|brighten)\\b", RegexOption.IGNORE_CASE))),
    ToolSpec("set_fan", listOf(Regex("\\bfan\\b", RegexOption.IGNORE_CASE))),
    ToolSpec("set_thermostat", listOf(Regex("\\b(thermostat|temperature|temp|heat|cool|ac)\\b", RegexOption.IGNORE_CASE), Regex("\\d+\\s*°"))),
    ToolSpec("lock_door", listOf(Regex("\\b(lock|unlock|deadbolt|lock up)\\b", RegexOption.IGNORE_CASE))),
    ToolSpec("set_volume", listOf(Regex("\\b(volume|mute|unmute)\\b", RegexOption.IGNORE_CASE))),
    ToolSpec("set_brightness", listOf(Regex("\\b(screen brightness|display brightness)\\b", RegexOption.IGNORE_CASE))),
    ToolSpec("set_dnd", listOf(Regex("\\b(do not disturb|don't disturb|\\bdnd\\b)\\b", RegexOption.IGNORE_CASE))),
    ToolSpec("set_wifi", listOf(Regex("\\b(wi-?fi|wlan)\\b", RegexOption.IGNORE_CASE))),
    ToolSpec("set_bluetooth", listOf(Regex("\\bbluetooth\\b", RegexOption.IGNORE_CASE))),
    ToolSpec("set_airplane", listOf(Regex("\\b(airplane|aeroplane) mode\\b", RegexOption.IGNORE_CASE))),
    ToolSpec("flash_torch", listOf(Regex("\\b(flashlight|torch)\\b", RegexOption.IGNORE_CASE))),
    ToolSpec("set_alarm", listOf(Regex("\\balarm\\b", RegexOption.IGNORE_CASE))),
    ToolSpec("start_timer", listOf(Regex("\\btimer\\b", RegexOption.IGNORE_CASE))),
    ToolSpec("play_query", listOf(Regex("\\bplay\\s+\\S", RegexOption.IGNORE_CASE))),
    ToolSpec("play_media", listOf(Regex("\\b(play|pause|skip|resume)\\b", RegexOption.IGNORE_CASE))),
    ToolSpec("start_navigation", listOf(Regex("\\b(navigate|navigation|directions|take me|drive to|walk to|bike to|nav to|route to|via|from)\\b", RegexOption.IGNORE_CASE))),
    ToolSpec("stop_navigation", listOf(Regex("\\b(stop navigation|cancel route|end navigation|stop navigating)\\b", RegexOption.IGNORE_CASE))),
    ToolSpec("open_settings", listOf(Regex("\\bsettings\\b|\\bopen bluetooth\\b", RegexOption.IGNORE_CASE))),
    ToolSpec("open_app", listOf(Regex("\\b(open|launch|start)\\b", RegexOption.IGNORE_CASE))),
    ToolSpec("dial_phone", listOf(Regex("\\b(call|dial)\\b", RegexOption.IGNORE_CASE))),
    ToolSpec("send_sms", listOf(Regex("\\b(text|sms|message)\\b", RegexOption.IGNORE_CASE))),
    ToolSpec("send_email", listOf(Regex("\\b(email|e-mail)\\b", RegexOption.IGNORE_CASE))),
    ToolSpec("maps_search", listOf(Regex("\\b(near me|nearby|around me|on maps)\\b", RegexOption.IGNORE_CASE))),
    ToolSpec("web_search", listOf(Regex("\\b(search|google|look up)\\b", RegexOption.IGNORE_CASE))),
    ToolSpec("open_url", listOf(Regex("https?://", RegexOption.IGNORE_CASE))),
    ToolSpec("add_event", listOf(Regex("\\b(event|meeting|appointment)\\b", RegexOption.IGNORE_CASE))),
    ToolSpec("copy_text", listOf(Regex("\\b(copy|clipboard)\\b", RegexOption.IGNORE_CASE))),
    ToolSpec("create_note", listOf(Regex("\\b(note|memo|remember)\\b", RegexOption.IGNORE_CASE))),
    ToolSpec("get_weather", listOf(Regex("\\b(weather|forecast)\\b", RegexOption.IGNORE_CASE))),
    ToolSpec("calculate", listOf(Regex("\\b(calculate|compute|what is \\d)", RegexOption.IGNORE_CASE))),
)

private val STOP_NAV = Regex("\\b(?:stop|cancel|end|quit)\\s+(?:the\\s+)?(?:nav|navigation|route|directions|trip)\\b|\\bstop navigating\\b", RegexOption.IGNORE_CASE)

private fun findRoom(text: String): String? {
    val lower = text.lowercase()
    return ROOMS.keys.sortedByDescending { it.length }.firstOrNull { key ->
        Regex("\\b${Regex.escape(key)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(lower)
    }?.let { ROOMS[it] }
}

private fun boolOnOff(text: String): Boolean? {
    val t = text.lowercase()
    if (Regex("\\bunlock\\b|\\bunmute\\b").containsMatchIn(t)) return false
    if (Regex("\\block up\\b|\\block\\b|\\bmute\\b").containsMatchIn(t)) return true
    if (Regex("\\b(off|disable|stop|close|pause)\\b").containsMatchIn(t) && !t.contains("turn on")) return false
    if (Regex("\\b(on|enable|start|open|resume|play|dim|brighten|toggle)\\b").containsMatchIn(t)) return true
    return null
}

private fun numberIn(text: String, min: Int, max: Int): Int? {
    Regex("(-?\\d+(?:\\.\\d+)?)\\s*°").find(text)?.groupValues?.get(1)?.toDoubleOrNull()?.toInt()?.let {
        if (it in min..max) return it
    }
    Regex("(?:to|at|level|speed|volume|brightness|percent|%)\\s*(-?\\d+)", RegexOption.IGNORE_CASE)
        .find(text)?.groupValues?.get(1)?.toIntOrNull()?.let { if (it in min..max) return it }
    return Regex("\\b(\\d{1,3})\\b").findAll(text).map { it.groupValues[1].toInt() }.firstOrNull { it in min..max }
}

private fun timeIn(text: String): String? {
    Regex("\\b(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)\\b", RegexOption.IGNORE_CASE).find(text)?.let { m ->
        var h = m.groupValues[1].toInt()
        val min = m.groupValues[2].ifEmpty { "00" }
        val mer = m.groupValues[3].lowercase()
        if (mer == "pm" && h < 12) h += 12
        if (mer == "am" && h == 12) h = 0
        return "%02d:%s".format(h, min)
    }
    Regex("\\b([01]?\\d|2[0-3]):([0-5]\\d)\\b").find(text)?.let {
        return "%02d:%s".format(it.groupValues[1].toInt(), it.groupValues[2])
    }
    return null
}

private fun settingsPanel(clause: String): String? {
    if (Regex("\\bopen bluetooth\\b", RegexOption.IGNORE_CASE).containsMatchIn(clause)) return "bluetooth"
    if (!Regex("\\bsettings\\b", RegexOption.IGNORE_CASE).containsMatchIn(clause)) return null
    val pairs = listOf(
        Regex("\\bwi-?fi\\b.*\\bsettings\\b|\\bsettings\\b.*\\bwi-?fi\\b", RegexOption.IGNORE_CASE) to "wifi",
        Regex("\\bbluetooth\\b.*\\bsettings\\b|\\bsettings\\b.*\\bbluetooth\\b", RegexOption.IGNORE_CASE) to "bluetooth",
        Regex("\\b(?:location|gps)\\b.*\\bsettings\\b|\\bsettings\\b.*\\b(?:location|gps)\\b", RegexOption.IGNORE_CASE) to "location",
        Regex("\\b(?:sound|volume)\\b.*\\bsettings\\b|\\bsettings\\b.*\\b(?:sound|volume)\\b", RegexOption.IGNORE_CASE) to "sound",
        Regex("\\b(?:display|brightness)\\b.*\\bsettings\\b|\\bsettings\\b.*\\b(?:display|brightness)\\b", RegexOption.IGNORE_CASE) to "display",
        Regex("\\b(?:airplane|aeroplane)\\b.*\\bsettings\\b|\\bsettings\\b.*\\b(?:airplane|aeroplane)\\b", RegexOption.IGNORE_CASE) to "airplane",
        Regex("\\bnfc\\b.*\\bsettings\\b|\\bsettings\\b.*\\bnfc\\b", RegexOption.IGNORE_CASE) to "nfc",
        Regex("\\bbattery\\b.*\\bsettings\\b|\\bsettings\\b.*\\bbattery\\b", RegexOption.IGNORE_CASE) to "battery",
        Regex("\\bnotification\\b.*\\bsettings\\b|\\bsettings\\b.*\\bnotification\\b", RegexOption.IGNORE_CASE) to "notifications",
        Regex("\\b(?:date|time)\\b.*\\bsettings\\b|\\bsettings\\b.*\\b(?:date|time)\\b", RegexOption.IGNORE_CASE) to "date",
        Regex("\\b(?:apps?|application)\\b.*\\bsettings\\b|\\bsettings\\b.*\\b(?:apps?|application)\\b", RegexOption.IGNORE_CASE) to "apps",
    )
    return pairs.firstOrNull { it.first.containsMatchIn(clause) }?.second ?: "settings"
}

private fun matchApp(clause: String): String? {
    if (!Regex("\\b(?:open|launch|start)\\b", RegexOption.IGNORE_CASE).containsMatchIn(clause)) return null
    return APPS.firstOrNull { Regex("\\b${Regex.escape(it)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(clause) }
}

private fun fill(tool: ToolSpec, clause: String, layer: Int): FunctionCall? {
    val t = clause.lowercase()
    val room = findRoom(clause)
    return when (tool.name) {
        "set_lights" -> {
            if (!Regex("\\b(lights?|lamp|bulbs?|dim|brighten|darken)\\b", RegexOption.IGNORE_CASE).containsMatchIn(clause)) return null
            val on = boolOnOff(clause) ?: true
            val args = mutableMapOf<String, Any>("room" to (room ?: "living"), "on" to on)
            if ("dim" in t && layer >= 5) args["brightness"] = 30
            if ("brighten" in t && layer >= 5) args["brightness"] = 100
            FunctionCall(tool.name, args)
        }
        "set_fan" -> {
            if (!t.contains("fan")) return null
            val args = mutableMapOf<String, Any>("room" to (room ?: "living"), "on" to (boolOnOff(clause) ?: true))
            numberIn(clause, 1, 3)?.takeIf { layer >= 5 }?.let { args["speed"] = it }
            FunctionCall(tool.name, args)
        }
        "set_thermostat" -> {
            if (!Regex("temp|thermostat|heat|cool|ac|°|set temperature", RegexOption.IGNORE_CASE).containsMatchIn(clause)) return null
            val temp = numberIn(clause, 10, 32) ?: return null
            val args = mutableMapOf<String, Any>("temperature" to temp)
            if ("heat" in t && layer >= 8) args["mode"] = "heat"
            if (Regex("cool|\\bac\\b").containsMatchIn(t) && layer >= 8) args["mode"] = "cool"
            FunctionCall(tool.name, args)
        }
        "lock_door" -> {
            if (!Regex("lock|unlock|deadbolt").containsMatchIn(t)) return null
            val door = if (room in listOf("garage", "office", "living")) room!! else "living"
            FunctionCall(tool.name, mapOf("door" to door, "locked" to !t.contains("unlock")))
        }
        "set_volume" -> when {
            "mute" in t && "unmute" !in t -> FunctionCall(tool.name, mapOf("level" to 0))
            "unmute" in t -> FunctionCall(tool.name, mapOf("level" to 40))
            "volume" in t -> numberIn(clause, 0, 100)?.let { FunctionCall(tool.name, mapOf("level" to it)) }
            else -> null
        }
        "set_brightness" -> if (Regex("screen brightness|display brightness").containsMatchIn(t))
            numberIn(clause, 0, 100)?.let { FunctionCall(tool.name, mapOf("level" to it)) } else null
        "set_dnd" -> if (Regex("do not disturb|don't disturb|\\bdnd\\b").containsMatchIn(t))
            FunctionCall(tool.name, mapOf("on" to (boolOnOff(clause) ?: true))) else null
        "set_wifi" -> if (Regex("wi-?fi|wlan").containsMatchIn(t) && !t.contains("settings"))
            FunctionCall(tool.name, mapOf("on" to (boolOnOff(clause) ?: true))) else null
        "set_bluetooth" -> if (Regex("\\bbluetooth\\b").containsMatchIn(t) && !t.contains("settings") && Regex("\\b(on|off|enable|disable|turn)\\b").containsMatchIn(t))
            FunctionCall(tool.name, mapOf("on" to (boolOnOff(clause) ?: true))) else null
        "set_airplane" -> if (Regex("airplane|aeroplane").containsMatchIn(t) && !t.contains("settings"))
            FunctionCall(tool.name, mapOf("on" to (boolOnOff(clause) ?: true))) else null
        "flash_torch" -> if (Regex("flashlight|torch").containsMatchIn(t))
            FunctionCall(tool.name, mapOf("on" to (boolOnOff(clause) ?: true))) else null
        "set_alarm" -> if ("alarm" in t) timeIn(clause)?.let { FunctionCall(tool.name, mapOf("time" to it)) } else null
        "start_timer" -> if ("timer" in t) numberIn(clause, 1, 180)?.let { FunctionCall(tool.name, mapOf("minutes" to it)) } else null
        "play_query" -> {
            val q = Regex("""^play\s+(.+)$""", RegexOption.IGNORE_CASE).find(clause.trim())?.groupValues?.get(1)?.trim()
            if (q != null && q.length >= 3 && !Regex("pause|skip|resume").containsMatchIn(t)) {
                FunctionCall(tool.name, mapOf("query" to q))
            } else null
        }
        "play_media" -> when {
            Regex("""^play\s+\S.{2,}""").containsMatchIn(clause.trim()) && !Regex("pause|skip|resume").containsMatchIn(t) -> null
            "skip" in t -> FunctionCall(tool.name, mapOf("action" to "skip"))
            "pause" in t -> FunctionCall(tool.name, mapOf("action" to "pause"))
            Regex("play|resume").containsMatchIn(t) -> FunctionCall(tool.name, mapOf("action" to "play"))
            else -> null
        }
        "start_navigation" -> {
            val plan = RouteText.parse(clause) ?: return null
            FunctionCall(
                tool.name,
                mapOf(
                    "origin" to plan.origin,
                    "destination" to plan.destination,
                    "waypoints" to plan.waypoints,
                    "mode" to plan.mode,
                    "avoid" to plan.avoid,
                ),
            )
        }
        "stop_navigation" -> if (STOP_NAV.containsMatchIn(clause) && RouteText.parse(clause) == null) FunctionCall(tool.name, emptyMap()) else null
        "open_settings" -> settingsPanel(clause)?.let { FunctionCall(tool.name, mapOf("panel" to it)) }
        "open_app" -> {
            if (settingsPanel(clause) != null) return null
            val app = matchApp(clause) ?: return null
            FunctionCall(tool.name, mapOf("app" to app, "label" to app.replaceFirstChar { it.uppercase() }))
        }
        "dial_phone" -> {
            val m = Regex("""\b(?:call|dial|phone)\s+([+]?\d[\d\s().-]{2,}|\w[\w\s]{0,32})""", RegexOption.IGNORE_CASE).find(clause)
            if (m == null || Regex("phone settings", RegexOption.IGNORE_CASE).containsMatchIn(clause)) null
            else FunctionCall(tool.name, mapOf("number" to m.groupValues[1].trim()))
        }
        "send_sms" -> {
            val m = Regex(
                """\b(?:text|sms|message)\s+([+]?\d[\d\s().-]{4,}|[A-Za-z][\w\s]{1,24}?)(?:\s+(?:saying|that|:|-)\s+(.+))?$""",
                RegexOption.IGNORE_CASE,
            ).find(clause)
            if (m == null) null else FunctionCall(tool.name, mapOf("number" to m.groupValues[1].trim(), "body" to m.groupValues[2].trim()))
        }
        "send_email" -> {
            val m = Regex("""\b(?:email|e-mail)\s+(\S+@\S+)(?:\s+(?:about|saying|that|:)\s+(.+))?$""", RegexOption.IGNORE_CASE).find(clause)
            if (m == null) null else FunctionCall(tool.name, mapOf("to" to m.groupValues[1], "subject" to m.groupValues[2].ifBlank { "Note from Latch" }.take(80), "body" to m.groupValues[2]))
        }
        "maps_search" -> {
            if (!Regex("""\b(?:near me|around me|nearby|on maps|in maps|find|search maps)\b""", RegexOption.IGNORE_CASE).containsMatchIn(clause)) return null
            val q = clause
                .replace(Regex("""\b(?:find|search maps for|search for|look for|look up)\b""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\b(?:near me|around me|nearby|on maps|in maps)\b""", RegexOption.IGNORE_CASE), "")
                .trim()
            if (q.length < 2) null else FunctionCall(tool.name, mapOf("query" to q))
        }
        "web_search" -> {
            if (Regex("""\b(?:near me|around me|nearby|on maps)\b""", RegexOption.IGNORE_CASE).containsMatchIn(clause)) return null
            val q = Regex("""\b(?:search|google|look up)\s+(?:for\s+|the web for\s+)?(.+)$""", RegexOption.IGNORE_CASE).find(clause)?.groupValues?.get(1)?.trim()
            if (q.isNullOrBlank() || q.length < 2) null else FunctionCall(tool.name, mapOf("query" to q))
        }
        "open_url" -> Regex("""\b(?:open|go to)\s+(https?://\S+)""", RegexOption.IGNORE_CASE).find(clause)?.groupValues?.get(1)?.let {
            FunctionCall(tool.name, mapOf("url" to it))
        }
        "add_event" -> Regex("""\b(?:add|create|schedule)\s+(?:a\s+)?(?:calendar\s+)?(?:event|meeting|appointment)\s+(.+)$""", RegexOption.IGNORE_CASE).find(clause)?.groupValues?.get(1)?.let {
            FunctionCall(tool.name, mapOf("title" to it.trim(), "time" to (timeIn(clause) ?: "")))
        }
        "copy_text" -> if (Regex("copy|clipboard").containsMatchIn(t)) {
            val text = clause.replace(Regex("""\b(?:copy|to my clipboard|clipboard)\b""", RegexOption.IGNORE_CASE), "").trim()
            if (text.isEmpty()) null else FunctionCall(tool.name, mapOf("text" to text))
        } else null
        "create_note" -> if (Regex("note|memo|remember").containsMatchIn(t) && RouteText.parse(clause) == null)
            FunctionCall(tool.name, mapOf("title" to clause.trim(), "body" to clause)) else null
        "get_weather" -> {
            if (!Regex("weather|forecast").containsMatchIn(t)) return null
            val city = Regex("\\b(?:in|for|at)\\s+([A-Za-z][a-z]+(?:\\s+[A-Za-z][a-z]+)?)", RegexOption.IGNORE_CASE)
                .find(clause)?.groupValues?.get(1) ?: "New York"
            FunctionCall(tool.name, mapOf("city" to city))
        }
        "calculate" -> Regex("(?:calculate|compute|what(?:'| i)?s)\\s+([\\d.+\\-*/() ]+)", RegexOption.IGNORE_CASE)
            .find(clause)?.groupValues?.get(1)?.let { FunctionCall(tool.name, mapOf("expression" to it.trim())) }
        else -> null
    }
}

private fun ladderCap(layer: Int) = when {
    layer <= 4 -> 1
    layer <= 8 -> 2
    layer <= 16 -> 3
    else -> 8
}

object NeedleEngine {
    fun complete(text: String, layer: Int = 20, auto: Double = 0.72): CompleteResult {
        val start = System.currentTimeMillis()
        val raw = text.trim()
        val depth = layer.coerceIn(2, 20)
        val prefill = 4200 - depth * 90
        val decode = 980 - depth * 18
        val ram = 14 + (depth * 0.7).toInt()
        if (raw.isEmpty()) return pack(emptyList(), emptyList(), "empty input", 0.0, false, depth, auto, prefill, decode, ram, start)

        val calls = mutableListOf<FunctionCall>()
        val bits = mutableListOf<String>()
        var triggered = false
        var confAcc = 0.0
        for (clause in RouteText.split(raw)) {
            if (calls.size >= ladderCap(depth)) break
            var best: Triple<ToolSpec, FunctionCall, Double>? = null
            var bestTrig = false
            for (tool in TOOLS) {
                val trig = tool.triggers.any { it.containsMatchIn(clause) }
                val call = fill(tool, clause, depth) ?: continue
                val bonus = if (tool.name == "start_navigation") 0.12 else if (tool.name == "open_settings" || tool.name == "maps_search" || tool.name == "dial_phone") 0.04 else 0.0
                val score = (if (trig) 0.9 else 0.62) + 0.08 + bonus
                if (best == null || score > best.third) {
                    best = Triple(tool, call, score)
                    bestTrig = trig
                }
            }
            val hit = best ?: continue
            triggered = triggered || bestTrig
            calls += hit.second
            confAcc += hit.third
            bits += "${hit.second.name}(${hit.second.arguments})"
        }
        var confidence = if (calls.isEmpty()) 0.0 else (confAcc / calls.size)
        if (depth <= 4) confidence = (confidence - 0.12).coerceAtLeast(0.1)
        val suppressed = mutableListOf<FunctionCall>()
        val kept = if (calls.isNotEmpty() && confidence < 0.1 && !triggered) {
            suppressed += calls
            bits += "confidence $confidence < 0.1 — suppressed"
            emptyList()
        } else calls
        val reasoning = bits.joinToString("; ").ifEmpty { "no tool matched — empty list, not a guess" }
        return pack(kept, suppressed, reasoning, confidence, triggered, depth, auto, prefill, decode, ram, start)
    }

    private fun pack(
        calls: List<FunctionCall>,
        suppressed: List<FunctionCall>,
        reasoning: String,
        confidence: Double,
        triggered: Boolean,
        layer: Int,
        auto: Double,
        prefill: Int,
        decode: Int,
        ram: Int,
        start: Long,
    ): CompleteResult {
        val gate = when {
            calls.isNotEmpty() && (confidence >= auto || (triggered && confidence >= 0.55)) -> Gate.EXECUTE
            calls.isNotEmpty() || suppressed.isNotEmpty() -> Gate.CONFIRM
            else -> Gate.REFUSE
        }
        return CompleteResult(
            type = if (calls.isNotEmpty()) "call" else "respond",
            functionCalls = calls,
            suppressedCalls = suppressed,
            reasoning = reasoning,
            confidence = if (calls.isNotEmpty() || suppressed.isNotEmpty()) confidence else null,
            prefillTps = prefill,
            decodeTps = decode,
            peakRamMb = ram,
            latencyMs = (System.currentTimeMillis() - start).coerceAtLeast(1),
            layer = layer,
            gate = gate,
            triggered = triggered,
        )
    }
}
