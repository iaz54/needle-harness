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
    var bluetooth: Boolean = true,
    var airplane: Boolean = false,
    var torch: Boolean = false,
    var alarm: String? = null,
    var timerMin: Int? = null,
    var nowPlaying: String? = null,
    var playing: Boolean = false,
    var navActive: Boolean = false,
    var navOrigin: String = "",
    var navDestination: String? = null,
    var navWaypoints: MutableList<String> = mutableListOf(),
    var navMode: String = "driving",
    var navAvoid: MutableList<String> = mutableListOf(),
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

private fun strings(value: Any?): MutableList<String> {
    val list = value as? List<*> ?: return mutableListOf()
    return list.mapNotNull { it as? String }.toMutableList()
}

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
        "set_bluetooth" -> {
            home.phone.bluetooth = a["on"] as Boolean
            "bluetooth ${home.phone.bluetooth}"
        }
        "set_airplane" -> {
            home.phone.airplane = a["on"] as Boolean
            "airplane ${home.phone.airplane}"
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
        "play_query" -> {
            val query = a["query"] as String
            home.phone.playing = true
            home.phone.nowPlaying = query
            "play $query"
        }
        "start_navigation" -> {
            home.phone.navActive = true
            home.phone.navOrigin = a["origin"] as? String ?: ""
            home.phone.navDestination = a["destination"] as String
            home.phone.navWaypoints = strings(a["waypoints"])
            home.phone.navMode = (a["mode"] as? String) ?: "driving"
            home.phone.navAvoid = strings(a["avoid"])
            val via = if (home.phone.navWaypoints.isEmpty()) "" else " via ${home.phone.navWaypoints.joinToString(" → ")}"
            val from = if (home.phone.navOrigin.isBlank()) "" else "${home.phone.navOrigin} → "
            "route ${home.phone.navMode} $from${home.phone.navDestination}$via"
        }
        "stop_navigation" -> {
            home.phone.navActive = false
            home.phone.navOrigin = ""
            home.phone.navDestination = null
            home.phone.navWaypoints = mutableListOf()
            home.phone.navMode = "driving"
            home.phone.navAvoid = mutableListOf()
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
        "open_settings" -> "settings ${a["panel"]}"
        "open_app" -> "open ${a["label"] ?: a["app"]}"
        "dial_phone" -> "dial ${a["number"]}"
        "send_sms" -> "sms ${a["number"]}"
        "send_email" -> "email ${a["to"]}"
        "web_search" -> "search ${a["query"]}"
        "maps_search" -> "maps ${a["query"]}"
        "open_url" -> "url ${a["url"]}"
        "add_event" -> "event ${a["title"]}"
        "copy_text" -> "copied"
        else -> "unknown tool"
    }
}
