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

private val TOOLS = listOf(
    ToolSpec("set_lights", listOf(Regex("\\b(lights?|lamp|bulbs?)\\b", RegexOption.IGNORE_CASE), Regex("\\b(dim|brighten)\\b", RegexOption.IGNORE_CASE))),
    ToolSpec("set_fan", listOf(Regex("\\bfan\\b", RegexOption.IGNORE_CASE))),
    ToolSpec("set_thermostat", listOf(Regex("\\b(thermostat|temperature|temp|heat|cool|ac)\\b", RegexOption.IGNORE_CASE), Regex("\\d+\\s*°"))),
    ToolSpec("lock_door", listOf(Regex("\\b(lock|unlock|deadbolt|lock up)\\b", RegexOption.IGNORE_CASE))),
    ToolSpec("set_volume", listOf(Regex("\\b(volume|mute|unmute)\\b", RegexOption.IGNORE_CASE))),
    ToolSpec("set_brightness", listOf(Regex("\\b(screen brightness|display brightness)\\b", RegexOption.IGNORE_CASE))),
    ToolSpec("set_dnd", listOf(Regex("\\b(do not disturb|don't disturb|\\bdnd\\b)\\b", RegexOption.IGNORE_CASE))),
    ToolSpec("set_wifi", listOf(Regex("\\b(wi-?fi|wlan)\\b", RegexOption.IGNORE_CASE))),
    ToolSpec("flash_torch", listOf(Regex("\\b(flashlight|torch)\\b", RegexOption.IGNORE_CASE))),
    ToolSpec("set_alarm", listOf(Regex("\\balarm\\b", RegexOption.IGNORE_CASE))),
    ToolSpec("start_timer", listOf(Regex("\\btimer\\b", RegexOption.IGNORE_CASE))),
    ToolSpec("play_media", listOf(Regex("\\b(play|pause|skip|resume)\\b", RegexOption.IGNORE_CASE))),
    ToolSpec("create_note", listOf(Regex("\\b(note|memo|remember)\\b", RegexOption.IGNORE_CASE))),
    ToolSpec("get_weather", listOf(Regex("\\b(weather|forecast)\\b", RegexOption.IGNORE_CASE))),
    ToolSpec("calculate", listOf(Regex("\\b(calculate|compute|what is \\d)", RegexOption.IGNORE_CASE))),
)

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

private fun splitClauses(text: String): List<String> {
    val parts = text.split(Regex("\\s*(?:,|;|\\band then\\b|\\bthen\\b|\\band\\b)\\s*", RegexOption.IGNORE_CASE))
        .map { it.trim() }.filter { it.isNotEmpty() }
    return parts.ifEmpty { listOf(text.trim()) }
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
        "set_wifi" -> if (Regex("wi-?fi|wlan").containsMatchIn(t))
            FunctionCall(tool.name, mapOf("on" to (boolOnOff(clause) ?: true))) else null
        "flash_torch" -> if (Regex("flashlight|torch").containsMatchIn(t))
            FunctionCall(tool.name, mapOf("on" to (boolOnOff(clause) ?: true))) else null
        "set_alarm" -> if ("alarm" in t) timeIn(clause)?.let { FunctionCall(tool.name, mapOf("time" to it)) } else null
        "start_timer" -> if ("timer" in t) numberIn(clause, 1, 180)?.let { FunctionCall(tool.name, mapOf("minutes" to it)) } else null
        "play_media" -> when {
            "skip" in t -> FunctionCall(tool.name, mapOf("action" to "skip"))
            "pause" in t -> FunctionCall(tool.name, mapOf("action" to "pause"))
            Regex("play|resume").containsMatchIn(t) -> FunctionCall(tool.name, mapOf("action" to "play"))
            else -> null
        }
        "create_note" -> if (Regex("note|memo|remember").containsMatchIn(t))
            FunctionCall(tool.name, mapOf("title" to clause.trim(), "body" to clause)) else null
        "get_weather" -> {
            if (!Regex("weather|forecast").containsMatchIn(t)) return null
            val city = Regex("\\b(?:in|for|at)\\s+([A-Z][a-z]+(?:\\s+[A-Z][a-z]+)?)", RegexOption.IGNORE_CASE)
                .find(clause)?.groupValues?.get(1)
                ?: Regex("\\b(Lagos|London|Paris|Tokyo|Columbus|Berlin)\\b", RegexOption.IGNORE_CASE).find(clause)?.value
                ?: return null
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
        for (clause in splitClauses(raw)) {
            if (calls.size >= ladderCap(depth)) break
            var best: Triple<ToolSpec, FunctionCall, Double>? = null
            var bestTrig = false
            for (tool in TOOLS) {
                val trig = tool.triggers.any { it.containsMatchIn(clause) }
                val call = fill(tool, clause, depth) ?: continue
                val score = (if (trig) 0.9 else 0.62) + 0.08
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
