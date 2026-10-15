package be.valuya.pingtray

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (app.settings.running) ProbeService.start(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
        permissions.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
        ))
        setContent { PingtrayApp() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** pingtray://config?url=…&token=…, from the QR code in pingtray's settings on the laptop. */
    private fun handle(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "pingtray" || uri.host != "config") return
        val s = app.settings
        uri.getQueryParameter("url")?.let { s.laptopUrl = it }
        uri.getQueryParameter("token")?.let { s.laptopToken = it }
        Toast.makeText(this, "Positions will be sent to ${s.laptopUrl}", Toast.LENGTH_LONG).show()
    }
}

@Composable
fun PingtrayApp() {
    MaterialTheme(colorScheme = darkColorScheme(background = Palette.BACKGROUND, surface = Palette.BACKGROUND)) {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        Column(Modifier.fillMaxSize().background(Palette.BACKGROUND).safeDrawingPadding()) {
            PrimaryTabRow(selectedTabIndex = tab, containerColor = Palette.BACKGROUND) {
                listOf("Graph", "Map", "Notes", "Settings").forEachIndexed { i, label ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
                }
            }
            when (tab) {
                0 -> GraphScreen()
                1 -> MapScreen()
                2 -> NotesScreen()
                else -> SettingsScreen()
            }
        }
    }
}
