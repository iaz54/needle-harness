package com.iaz54.needleharness

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import com.iaz54.needleharness.engine.FunctionCall

object SystemActions {
    private val SETTINGS = mapOf(
        "wifi" to Settings.ACTION_WIFI_SETTINGS,
        "bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS,
        "location" to Settings.ACTION_LOCATION_SOURCE_SETTINGS,
        "sound" to Settings.ACTION_SOUND_SETTINGS,
        "display" to Settings.ACTION_DISPLAY_SETTINGS,
        "airplane" to Settings.ACTION_AIRPLANE_MODE_SETTINGS,
        "nfc" to Settings.ACTION_NFC_SETTINGS,
        "battery" to Settings.ACTION_BATTERY_SAVER_SETTINGS,
        "notifications" to Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS,
        "date" to Settings.ACTION_DATE_SETTINGS,
        "apps" to Settings.ACTION_APPLICATION_SETTINGS,
        "settings" to Settings.ACTION_SETTINGS,
    )

    private val APPS = mapOf(
        "spotify" to "com.spotify.music",
        "youtube" to "com.google.android.youtube",
        "camera" to "com.google.android.GoogleCamera",
        "chrome" to "com.android.chrome",
        "gmail" to "com.google.android.gm",
        "messages" to "com.google.android.apps.messaging",
        "calendar" to "com.google.android.calendar",
        "clock" to "com.google.android.deskclock",
        "photos" to "com.google.android.apps.photos",
        "whatsapp" to "com.whatsapp",
        "instagram" to "com.instagram.android",
        "calculator" to "com.google.android.calculator",
        "files" to "com.google.android.documentsui",
        "play store" to "com.android.vending",
        "maps" to "com.google.android.apps.maps",
        "settings" to "com.android.settings",
        "phone" to "com.google.android.dialer",
    )

    fun dispatch(context: Context, calls: List<FunctionCall>) {
        for (call in calls) {
            try {
                when (call.name) {
                    "start_navigation" -> launchRoute(context, call)
                    "open_settings" -> openSettings(context, call.arguments["panel"] as? String ?: "settings")
                    "set_wifi" -> openSettings(context, "wifi")
                    "set_bluetooth" -> openSettings(context, "bluetooth")
                    "set_airplane" -> openSettings(context, "airplane")
                    "set_dnd" -> openSettings(context, "notifications")
                    "set_brightness" -> openSettings(context, "display")
                    "set_volume" -> setVolume(context, (call.arguments["level"] as? Number)?.toInt() ?: 40)
                    "set_alarm" -> setAlarm(context, call.arguments["time"] as? String ?: continue)
                    "start_timer" -> setTimer(context, (call.arguments["minutes"] as? Number)?.toInt() ?: 1)
                    "dial_phone" -> dial(context, call.arguments["number"] as? String ?: continue)
                    "send_sms" -> sms(context, call.arguments["number"] as? String ?: "", call.arguments["body"] as? String ?: "")
                    "send_email" -> email(context, call.arguments["to"] as? String ?: continue, call.arguments["subject"] as? String ?: "", call.arguments["body"] as? String ?: "")
                    "open_app" -> openApp(context, call.arguments["app"] as? String ?: continue)
                    "maps_search" -> openMapsSearch(context, call.arguments["query"] as? String ?: continue)
                    "web_search" -> view(context, Uri.parse("https://www.google.com/search?q=${Uri.encode(call.arguments["query"] as? String ?: "")}"))
                    "open_url" -> view(context, Uri.parse(call.arguments["url"] as? String ?: continue))
                    "play_query" -> view(context, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(call.arguments["query"] as? String ?: "")}"))
                    "add_event" -> addEvent(context, call.arguments["title"] as? String ?: "Latch")
                }
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
    }

    fun launchRoute(context: Context, call: FunctionCall) {
        val destination = call.arguments["destination"] as? String ?: return
        val origin = call.arguments["origin"] as? String ?: ""
        val waypoints = stringList(call.arguments["waypoints"])
        val mode = call.arguments["mode"] as? String ?: "driving"
        val avoid = stringList(call.arguments["avoid"])
        launchRoute(context, origin, destination, waypoints, mode, avoid)
    }

    fun launchRoute(
        context: Context,
        origin: String,
        destination: String,
        waypoints: List<String>,
        mode: String,
        avoid: List<String>,
    ) {
        val stops = buildList {
            if (origin.isNotBlank()) add(origin)
            addAll(waypoints.filter { it.isNotBlank() })
            add(destination)
        }
        if (stops.size <= 1) {
            val avoidBits = avoidLetters(avoid)
            val nav = Uri.parse(
                "google.navigation:q=${Uri.encode(destination)}&mode=${modeLetter(mode)}" +
                    if (avoidBits.isEmpty()) "" else "&avoid=$avoidBits",
            )
            val maps = Intent(Intent.ACTION_VIEW, nav).setPackage("com.google.android.apps.maps").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(maps)
                return
            } catch (_: ActivityNotFoundException) {
            }
        }
        val url = mapsUrl(origin, destination, waypoints, mode, avoid)
        val packed = Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage("com.google.android.apps.maps").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(packed)
        } catch (_: ActivityNotFoundException) {
            view(context, Uri.parse(url))
        }
    }

    private fun mapsUrl(origin: String, destination: String, waypoints: List<String>, mode: String, avoid: List<String>): String {
        val chain = (waypoints.filter { it.isNotBlank() } + destination).joinToString("+to:") { Uri.encode(it) }
        val saddr = if (origin.isBlank()) "" else "&saddr=${Uri.encode(origin)}"
        return "https://maps.google.com/maps?f=d$saddr&daddr=$chain&dirflg=${avoidLetters(avoid)}${modeLetter(mode)}"
    }

    private fun avoidLetters(avoid: List<String>) = buildString {
        if ("tolls" in avoid) append('t')
        if ("highways" in avoid) append('h')
        if ("ferries" in avoid) append('f')
    }

    private fun modeLetter(mode: String) = when (mode) {
        "walking" -> "w"
        "bicycling" -> "b"
        "transit" -> "r"
        else -> "d"
    }

    private fun openSettings(context: Context, panel: String) {
        val action = SETTINGS[panel] ?: Settings.ACTION_SETTINGS
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun setVolume(context: Context, level: Int) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val scaled = ((level.coerceIn(0, 100) / 100f) * max).toInt()
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, scaled, AudioManager.FLAG_SHOW_UI)
    }

    private fun setAlarm(context: Context, time: String) {
        val bits = time.split(":")
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, bits.getOrNull(0)?.toIntOrNull() ?: 7)
            putExtra(AlarmClock.EXTRA_MINUTES, bits.getOrNull(1)?.toIntOrNull() ?: 0)
            putExtra(AlarmClock.EXTRA_MESSAGE, "Latch")
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun setTimer(context: Context, minutes: Int) {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, minutes.coerceIn(1, 180) * 60)
            putExtra(AlarmClock.EXTRA_MESSAGE, "Latch")
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun dial(context: Context, number: String) {
        val digits = number.filter { it.isDigit() || it == '+' }
        val uri = if (digits.length >= 3) Uri.parse("tel:$digits") else Uri.parse("tel:")
        context.startActivity(Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun sms(context: Context, number: String, body: String) {
        val digits = number.filter { it.isDigit() || it == '+' }
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$digits")).apply {
            putExtra("sms_body", body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun email(context: Context, to: String, subject: String, body: String) {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$to")).apply {
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun openApp(context: Context, key: String) {
        val pkg = APPS[key]
        val launch = pkg?.let { context.packageManager.getLaunchIntentForPackage(it) }
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launch)
            return
        }
        if (key == "maps") openMapsSearch(context, "")
    }

    private fun openMapsSearch(context: Context, query: String) {
        val uri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(query)}")
        val maps = Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(maps)
        } catch (_: ActivityNotFoundException) {
            view(context, uri)
        }
    }

    private fun addEvent(context: Context, title: String) {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = android.provider.CalendarContract.Events.CONTENT_URI
            putExtra(android.provider.CalendarContract.Events.TITLE, title)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            view(context, Uri.parse("https://calendar.google.com/calendar/render?action=TEMPLATE&text=${Uri.encode(title)}"))
        }
    }

    private fun view(context: Context, uri: Uri) {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun stringList(value: Any?): List<String> {
        val list = value as? List<*> ?: return emptyList()
        return list.mapNotNull { it as? String }
    }
}
