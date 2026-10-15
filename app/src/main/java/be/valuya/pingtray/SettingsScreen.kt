package be.valuya.pingtray

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val s = context.app.settings
    val targets = remember { mutableStateListOf(*s.targets.toTypedArray()) }
    var interval by remember { mutableStateOf(s.intervalSecs.toString()) }
    var httpInterval by remember { mutableStateOf(s.httpIntervalSecs.toString()) }
    var laptopUrl by remember { mutableStateOf(s.laptopUrl) }
    var running by remember { mutableStateOf(s.running) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Measure in the background", Modifier.weight(1f), color = Palette.TEXT)
            Switch(running, {
                running = it
                s.running = it
                if (it) ProbeService.start(context) else ProbeService.stop(context)
            })
        }
        Section("Destinations")
        targets.forEachIndexed { i, t ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(t.name, { targets[i] = t.copy(name = it) }, Modifier.weight(1f), label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(t.host, { targets[i] = t.copy(host = it) }, Modifier.weight(1f), label = { Text("Ping host") }, singleLine = true)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(t.http ?: "", { targets[i] = t.copy(http = it.ifBlank { null }) }, Modifier.weight(1f), label = { Text("HTTP URL (optional)") }, singleLine = true)
                    TextButton(onClick = { targets.removeAt(i) }) { Text("Remove") }
                }
            }
        }
        TextButton(onClick = { targets.add(Target("", "", null)) }) { Text("+ Add destination") }

        Section("Timing")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(interval, { interval = it }, Modifier.weight(1f), label = { Text("Ping every (s)") }, singleLine = true)
            OutlinedTextField(httpInterval, { httpInterval = it }, Modifier.weight(1f), label = { Text("HTTP every (s)") }, singleLine = true)
        }

        Section("pingtray on the laptop")
        Text(
            "Positions go to pingtray on the laptop (queued while it is out of reach). " +
                "Easiest: scan the QR code in pingtray's settings on the laptop (Phone positions).",
            color = Palette.WEAK, fontSize = 12.sp,
        )
        OutlinedTextField(laptopUrl, { laptopUrl = it }, label = { Text("URL, e.g. http://ymajoros.local:18765/pub") }, singleLine = true)
        Text(
            if (s.laptopToken.isBlank()) "No token yet: scan the QR code." else "Token set. ${ProbeService.lastUpload ?: ""}",
            color = Palette.WEAK, fontSize = 12.sp,
        )

        Button(onClick = {
            val clean = targets.filter { it.name.isNotBlank() && it.host.isNotBlank() }
            s.targets = clean
            interval.toFloatOrNull()?.takeIf { it >= 0.5f }?.let { s.intervalSecs = it }
            httpInterval.toFloatOrNull()?.takeIf { it >= 1f }?.let { s.httpIntervalSecs = it }
            s.laptopUrl = laptopUrl
            if (s.running) {
                ProbeService.stop(context)
                ProbeService.start(context)
            }
            message = "Saved; measurements restarted."
        }) { Text("Save") }
        message?.let { Text(it, color = Palette.GOOD) }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, color = Palette.TEXT, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
}
