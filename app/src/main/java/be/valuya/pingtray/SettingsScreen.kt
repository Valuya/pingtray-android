package be.valuya.pingtray

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp

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
    val field = RoundedCornerShape(14.dp)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        Group {
            ListItem(
                headlineContent = { Text("Measure in the background") },
                supportingContent = { Text("Pings, HTTP probes and GPS, with a status notification") },
                trailingContent = {
                    Switch(running, {
                        running = it
                        s.running = it
                        if (it) ProbeService.start(context) else ProbeService.stop(context)
                    })
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        }

        Text("Destinations", style = MaterialTheme.typography.titleMedium)
        targets.forEachIndexed { i, t ->
            Group {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(t.name, { targets[i] = t.copy(name = it) }, Modifier.weight(1f), label = { Text("Name") }, singleLine = true, shape = field)
                        IconButton(onClick = { targets.removeAt(i) }) { Icon(Icons.Rounded.Close, contentDescription = "Remove") }
                    }
                    OutlinedTextField(t.host, { targets[i] = t.copy(host = it) }, Modifier.fillMaxWidth(), label = { Text("Ping host or IP") }, singleLine = true, shape = field)
                    OutlinedTextField(t.http ?: "", { targets[i] = t.copy(http = it.ifBlank { null }) }, Modifier.fillMaxWidth(), label = { Text("HTTP URL (optional)") }, singleLine = true, shape = field)
                }
            }
        }
        TextButton(onClick = { targets.add(Target("", "", null)) }) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Text(" Add destination")
        }

        Text("Timing", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(interval, { interval = it }, Modifier.weight(1f), label = { Text("Ping every (s)") }, singleLine = true, shape = field)
            OutlinedTextField(httpInterval, { httpInterval = it }, Modifier.weight(1f), label = { Text("HTTP every (s)") }, singleLine = true, shape = field)
        }

        Text("pingtray on the laptop", style = MaterialTheme.typography.titleMedium)
        Group {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Positions are sent to pingtray on the laptop, and queued while it is out of reach. " +
                        "Easiest: scan the \"set up pingtray for Android\" QR code in pingtray's settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(laptopUrl, { laptopUrl = it }, Modifier.fillMaxWidth(), label = { Text("URL") }, placeholder = { Text("http://ymajoros.local:18765/pub") }, singleLine = true, shape = field)
                Text(
                    if (s.laptopToken.isBlank()) "No token yet: scan the QR code." else "Token set. ${ProbeService.lastUpload ?: ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Button(
            onClick = {
                s.targets = targets.filter { it.name.isNotBlank() && it.host.isNotBlank() }
                interval.toFloatOrNull()?.takeIf { it >= 0.5f }?.let { s.intervalSecs = it }
                httpInterval.toFloatOrNull()?.takeIf { it >= 1f }?.let { s.httpIntervalSecs = it }
                s.laptopUrl = laptopUrl
                if (s.running) {
                    ProbeService.stop(context)
                    ProbeService.start(context)
                }
                message = "Saved, measurements restarted"
            },
            Modifier.fillMaxWidth(),
        ) { Text("Save") }
        message?.let { Text(it, color = Palette.GOOD, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        content = content,
    )
}
