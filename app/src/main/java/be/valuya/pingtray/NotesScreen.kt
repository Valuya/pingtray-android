package be.valuya.pingtray

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What the graph cannot know: "crowded train", "Wi-Fi on board"… */
@Composable
fun NotesScreen() {
    val db = LocalContext.current.app.db
    var text by remember { mutableStateOf("") }
    var version by remember { mutableIntStateOf(0) }
    val frequent = remember(version) { db.frequentNotes(8) }
    val recent = remember(version) { db.recentNotes(50) }
    val fmt = SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault())
    fun add(t: String) {
        if (t.isBlank()) return
        db.addNote(System.currentTimeMillis(), t.trim())
        text = ""
        version++
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Notes", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp))
        Text(
            "What the graph cannot know: they show on the charts and in their details.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                text, { text = it }, Modifier.weight(1f),
                placeholder = { Text("crowded, quiet carriage, Wi-Fi on board…") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )
            FilledIconButton(onClick = { add(text) }) { Icon(Icons.Rounded.Add, contentDescription = "Add") }
        }
        if (frequent.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                frequent.forEach { f -> SuggestionChip(onClick = { add(f) }, label = { Text(f) }) }
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(recent, key = { it.id }) { n ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    ListItem(
                        headlineContent = { Text(n.text) },
                        supportingContent = { Text(fmt.format(Date(n.ts))) },
                        trailingContent = {
                            IconButton(onClick = { db.deleteNote(n.id); version++ }) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete")
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    )
                }
            }
        }
    }
}
