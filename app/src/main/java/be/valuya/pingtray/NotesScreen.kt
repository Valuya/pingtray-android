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
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    val recent = remember(version) { db.recentNotes(30) }
    val fmt = SimpleDateFormat("EEE d/MM HH:mm", Locale.getDefault())
    fun add(t: String) {
        if (t.isBlank()) return
        db.addNote(System.currentTimeMillis(), t.trim())
        text = ""
        version++
    }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(text, { text = it }, Modifier.weight(1f), placeholder = { Text("crowded, quiet carriage…") }, singleLine = true)
            Button(onClick = { add(text) }) { Text("Add") }
        }
        if (frequent.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                frequent.forEach { f -> SuggestionChip(onClick = { add(f) }, label = { Text(f) }) }
            }
        }
        LazyColumn {
            items(recent, key = { it.id }) { n ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(fmt.format(Date(n.ts)), color = Palette.WEAK, modifier = Modifier.padding(end = 12.dp))
                    Text(n.text, color = Palette.TEXT, modifier = Modifier.weight(1f))
                    TextButton(onClick = { db.deleteNote(n.id); version++ }) { Text("Delete") }
                }
            }
        }
    }
}
