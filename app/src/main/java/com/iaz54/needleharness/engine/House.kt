package com.iaz54.needleharness.engine

data class RoomState(
    val id: String,
    val label: String,
    var light: Boolean = false,
    var brightness: Int = 70,
    var fan: Boolean = false,
    var fanSpeed: Int = 2,
    var locked: Boolean = false,
)

data class PhoneState(
    var volume: Int = 40,
    var brightness: Int = 55,
    var dnd: Boolean = false,
    var wifi: Boolean = true,
    var torch: Boolean = false,
    var alarm: String? = null,
    var timerMin: Int? = null,
    var nowPlaying: String? = null,
    var playing: Boolean = false,
    var navActive: Boolean = false,
    var navDestination: String? = null,
    var navMode: String = "driving",
)

data class HomeState(
    val rooms: MutableMap<String, RoomState>,
    var temperature: Int = 21,
    var mode: String = "auto",
    val phone: PhoneState = PhoneState(),
    val notes: MutableList<Pair<String, String>> = mutableListOf(
        "Filter change" to "Kitchen fridge water filter due next month",
        "Garage code" to "Guest keypad is rotated every quarter",
    ),
    val revision: Int = 0,
)

fun emptyHome(): HomeState {
    val ids = listOf(
        "living" to "Living room",
        "kitchen" to "Kitchen",
        "bedroom" to "Bedroom",
        "office" to "Office",
        "bathroom" to "Bathroom",
        "garage" to "Garage",
    )
    val rooms = ids.associate { (id, label) ->
        id to RoomState(id, label, locked = id == "garage")
    }.toMutableMap()
    return HomeState(rooms)
}

fun bump(home: HomeState): HomeState = home.copy(revision = home.revision + 1)

fun execute(home: HomeState, call: FunctionCall): String {
    val a = call.arguments
    fun room(key: String) = home.rooms[a[key] as? String] ?: home.rooms.getValue("living")
    return when (call.name) {
        "set_lights" -> {
            val r = room("room")
            r.light = a["on"] as Boolean
            (a["brightness"] as? Number)?.let { r.brightness = it.toInt() }
            "${r.label} lights ${if (r.light) "on ${r.brightness}%" else "off"}"
        }
        "set_fan" -> {
            val r = room("room")
            r.fan = a["on"] as Boolean
            (a["speed"] as? Number)?.let { r.fanSpeed = it.toInt() }
            "${r.label} fan ${if (r.fan) "on x${r.fanSpeed}" else "off"}"
        }
        "set_thermostat" -> {
            home.temperature = (a["temperature"] as Number).toInt()
            (a["mode"] as? String)?.let { home.mode = it }
            "thermostat ${home.temperature}°C ${home.mode}"
        }
        "lock_door" -> {
            val r = room("door")
            r.locked = a["locked"] as Boolean
            "${r.label} ${if (r.locked) "locked" else "unlocked"}"
        }
        "set_volume" -> {
            home.phone.volume = (a["level"] as Number).toInt()
            "volume ${home.phone.volume}"
        }
        "set_brightness" -> {
            home.phone.brightness = (a["level"] as Number).toInt()
            "screen ${home.phone.brightness}"
        }
        "set_dnd" -> {
            home.phone.dnd = a["on"] as Boolean
            "dnd ${home.phone.dnd}"
        }
        "set_wifi" -> {
            home.phone.wifi = a["on"] as Boolean
            "wifi ${home.phone.wifi}"
        }
        "flash_torch" -> {
            home.phone.torch = a["on"] as Boolean
            "torch ${home.phone.torch}"
        }
        "set_alarm" -> {
            home.phone.alarm = a["time"] as String
            "alarm ${home.phone.alarm}"
        }
        "start_timer" -> {
            home.phone.timerMin = (a["minutes"] as Number).toInt()
            "timer ${home.phone.timerMin}m"
        }
        "play_media" -> {
            val action = a["action"] as String
            home.phone.playing = action != "pause"
            home.phone.nowPlaying = if (action == "skip") "Next track" else home.phone.nowPlaying ?: "Queue"
            "$action ${home.phone.nowPlaying}"
        }
        "start_navigation" -> {
            home.phone.navActive = true
            home.phone.navDestination = a["destination"] as String
            home.phone.navMode = (a["mode"] as? String) ?: "driving"
            "navigate ${home.phone.navMode} to ${home.phone.navDestination}"
        }
        "stop_navigation" -> {
            home.phone.navActive = false
            home.phone.navDestination = null
            home.phone.navMode = "driving"
            "navigation stopped"
        }
        "create_note" -> {
            val title = a["title"] as String
            home.notes.add(0, title to (a["body"] as? String ?: title))
            "note $title"
        }
        "get_weather" -> {
            val city = a["city"] as String
            val temp = 18 + city.length % 14
            "$city ${temp}°C"
        }
        "calculate" -> {
            val expr = (a["expression"] as String).filter { it.isDigit() || it in ".+-*/() " }
            "calc $expr"
        }
        else -> "unknown tool"
    }
}
