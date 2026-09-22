package com.iaz54.needleharness

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iaz54.needleharness.engine.CompleteResult
import com.iaz54.needleharness.engine.FunctionCall
import com.iaz54.needleharness.engine.Gate
import com.iaz54.needleharness.engine.HomeState
import com.iaz54.needleharness.engine.NeedleEngine
import com.iaz54.needleharness.engine.bump
import com.iaz54.needleharness.engine.emptyHome
import com.iaz54.needleharness.engine.execute

private val Ink = Color(0xFF0A0B0A)
private val Elevated = Color(0xFF121412)
private val Fg = Color(0xFFF2F4F2)
private val Muted = Color(0xFF8B918C)
private val Line = Color(0x22F2F4F2)
private val Execute = Color(0xFF6EE7A8)
private val Confirm = Color(0xFFC4B7A1)
private val Paper = Color(0xFFD7DDD8)

private fun modeLetter(mode: String) = when (mode) {
    "walking" -> "w"
    "bicycling" -> "b"
    "transit" -> "r"
    else -> "d"
}

private fun launchMaps(context: android.content.Context, destination: String, mode: String) {
    val encoded = Uri.encode(destination)
    val nav = Uri.parse("google.navigation:q=$encoded&mode=${modeLetter(mode)}")
    val maps = Intent(Intent.ACTION_VIEW, nav).apply {
        setPackage("com.google.android.apps.maps")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(maps)
        return
    } catch (_: ActivityNotFoundException) {
        // fall through
    }
    val web = Uri.parse(
        "https://www.google.com/maps/dir/?api=1&destination=$encoded&travelmode=$mode",
    )
    context.startActivity(Intent(Intent.ACTION_VIEW, web).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun maybeLaunchNav(context: android.content.Context, calls: List<FunctionCall>) {
    for (call in calls) {
        if (call.name != "start_navigation") continue
        val dest = call.arguments["destination"] as? String ?: continue
        val mode = call.arguments["mode"] as? String ?: "driving"
        launchMaps(context, dest, mode)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LatchApp() }
    }
}

@Composable
fun LatchApp() {
    val context = LocalContext.current
    var home by remember { mutableStateOf(emptyHome()) }
    var input by remember { mutableStateOf("") }
    var last by remember { mutableStateOf<CompleteResult?>(null) }
    var pending by remember { mutableStateOf<CompleteResult?>(null) }
    var layer by remember { mutableIntStateOf(20) }
    var log by remember { mutableStateOf(listOf<String>()) }

    fun applyCalls(result: CompleteResult) {
        val calls = result.functionCalls.ifEmpty { result.suppressedCalls }
        val msgs = calls.map { execute(home, it) }
        home = bump(home)
        log = listOf("EXECUTE · ${msgs.joinToString(" → ")}") + log
        maybeLaunchNav(context, calls)
    }

    fun run(text: String) {
        val result = NeedleEngine.complete(text, layer)
        last = result
        when (result.gate) {
            Gate.EXECUTE -> applyCalls(result)
            Gate.CONFIRM -> pending = result
            Gate.REFUSE -> log = listOf("REFUSE · ${result.reasoning}") + log
        }
        input = ""
    }

    fun confirm(yes: Boolean) {
        val p = pending ?: return
        if (yes) applyCalls(p) else log = listOf("blocked") + log
        pending = null
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .statusBarsPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "NEEDLE 3  ·  HARNESS",
            color = Muted,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp,
        )
        Text("Latch", color = Fg, fontSize = 36.sp)
        Text("Models that act, not chat. Commands stay on this phone.", color = Muted, fontSize = 14.sp)
        NavBanner(home)
        HouseGrid(home)
        Text(
            "${layer}L  ·  ${14 + (layer * 0.7).toInt()} MB rung",
            color = Muted,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
        )
        Slider(
            value = layer.toFloat(),
            onValueChange = { layer = (it.toInt() / 2) * 2 },
            valueRange = 2f..20f,
            steps = 8,
            colors = SliderDefaults.colors(
                thumbColor = Execute,
                activeTrackColor = Execute,
                inactiveTrackColor = Line,
            ),
        )
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("navigate to the airport…", color = Muted) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { if (input.isNotBlank()) run(input) }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Fg,
                unfocusedTextColor = Fg,
                focusedBorderColor = Paper,
                unfocusedBorderColor = Line,
                cursorColor = Execute,
            ),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { if (input.isNotBlank()) run(input) },
                colors = ButtonDefaults.buttonColors(containerColor = Paper, contentColor = Ink),
            ) { Text("Run") }
            TextButton(onClick = {
                home = emptyHome()
                last = null
                log = emptyList()
            }) { Text("Reset", color = Muted) }
        }
        SampleChips { run(it) }
        last?.let { ResultCard(it) }
        pending?.let { GateSheet(it, ::confirm) }
        log.take(6).forEach {
            Text(it, color = Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SampleChips(onRun: (String) -> Unit) {
    val samples = listOf(
        "navigate to the airport",
        "take me home",
        "walk to the grocery store",
        "turn on the fan, set temperature to 10°, turn on bedroom light",
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        samples.forEach { s ->
            Text(
                s,
                color = Muted,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Line, RoundedCornerShape(20.dp))
                    .clickable { onRun(s) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun NavBanner(home: HomeState) {
    val dest = home.phone.navDestination
    if (!home.phone.navActive || dest.isNullOrBlank()) {
        Text(
            "Say “navigate to the airport” — Latch opens Maps on this phone.",
            color = Muted,
            fontSize = 13.sp,
        )
        return
    }
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, Execute, RoundedCornerShape(16.dp))
            .background(Elevated, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("NAVIGATING  ·  ${home.phone.navMode.uppercase()}", color = Execute, fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = 2.sp)
        Text(dest, color = Fg, fontSize = 18.sp)
        Button(
            onClick = { launchMaps(context, dest, home.phone.navMode) },
            colors = ButtonDefaults.buttonColors(containerColor = Execute, contentColor = Ink),
        ) { Text("Open in Maps") }
    }
}

@Composable
private fun HouseGrid(home: HomeState) {
    val order = listOf("living", "kitchen", "bedroom", "office", "bathroom", "garage")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Thermostat ${home.temperature}°C  ${home.mode}",
            color = Execute,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
        order.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { id ->
                    val r = home.rooms.getValue(id)
                    Column(
                        Modifier
                            .weight(1f)
                            .border(1.dp, if (r.light) Color(0x55F3EAD2) else Line, RoundedCornerShape(12.dp))
                            .background(Elevated, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                    ) {
                        Text(r.label, color = Fg, fontSize = 13.sp)
                        Text(
                            "light ${if (r.light) "${r.brightness}%" else "off"}  fan ${if (r.fan) "on" else "off"}  ${if (r.locked) "locked" else "open"}",
                            color = Muted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultCard(result: CompleteResult) {
    val tone = when (result.gate) {
        Gate.EXECUTE -> Execute
        Gate.CONFIRM -> Confirm
        Gate.REFUSE -> Color(0xFFC98980)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, Line, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "${result.gate}  ${(result.confidence?.times(100)?.toInt() ?: 0)}%  ${result.latencyMs}ms  ${result.layer}L",
            color = tone,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
        )
        Text(result.reasoning, color = Muted, fontSize = 13.sp)
        result.functionCalls.forEach { call ->
            Text("${call.name} ${call.arguments}", color = Fg, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
        if (result.functionCalls.isEmpty()) {
            Text("function_calls: []", color = Muted, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
    }
}

@Composable
private fun GateSheet(result: CompleteResult, onConfirm: (Boolean) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, Confirm, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("CONFIRM", color = Confirm, fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = 2.sp)
        Text("Needle is not sure", color = Fg, fontSize = 16.sp)
        (result.functionCalls.ifEmpty { result.suppressedCalls }).forEach {
            Text("${it.name} ${it.arguments}", color = Muted, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onConfirm(false) }) { Text("Block", color = Muted) }
            Button(
                onClick = { onConfirm(true) },
                colors = ButtonDefaults.buttonColors(containerColor = Execute, contentColor = Ink),
            ) { Text("Execute") }
        }
    }
}
