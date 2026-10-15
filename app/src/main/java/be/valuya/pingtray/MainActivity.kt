package be.valuya.pingtray

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
        enableEdgeToEdge()
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

private enum class Screen(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Graph("Graph", Icons.Rounded.ShowChart),
    Map("Map", Icons.Rounded.Map),
    Notes("Notes", Icons.Rounded.EditNote),
    Settings("Settings", Icons.Rounded.Settings),
}

@Composable
fun PingtrayApp() {
    PingtrayTheme {
        var screen by rememberSaveable { mutableStateOf(Screen.Graph) }
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            bottomBar = {
                NavigationBar {
                    Screen.entries.forEach { s ->
                        NavigationBarItem(
                            selected = screen == s,
                            onClick = { screen = s },
                            icon = { Icon(s.icon, contentDescription = null) },
                            label = { Text(s.label) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (screen) {
                    Screen.Graph -> GraphScreen()
                    Screen.Map -> MapScreen()
                    Screen.Notes -> NotesScreen()
                    Screen.Settings -> SettingsScreen()
                }
            }
        }
    }
}
