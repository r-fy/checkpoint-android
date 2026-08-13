package org.rfisolns.checkpoint

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        ContextCompat.startForegroundService(this, Intent(this, CheckpointSessionService::class.java))

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CheckpointScreen(context = this)
                }
            }
        }
    }
}

@Composable
private fun CheckpointScreen(context: ComponentActivity) {
    val sessionActive by CheckpointSessionState.sessionActive.collectAsState()
    val manualOverride by CheckpointSessionState.manualOverride.collectAsState()

    var paused by remember { mutableStateOf(Settings.isPaused(context)) }
    var intervalMinutes by remember { mutableStateOf(Settings.getIntervalMinutes(context)) }
    var autoStart by remember { mutableStateOf(Settings.isAutoStartOnUnlockEnabled(context)) }
    var entries by remember { mutableStateOf(ActivityLogStore.todayEntries(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                entries = ActivityLogStore.todayEntries(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Text("Checkpoint", style = MaterialTheme.typography.headlineMedium)
            Text(
                if (sessionActive) "Session active" else "Session idle",
                style = MaterialTheme.typography.bodyLarge,
            )

            Button(
                onClick = {
                    val action = if (manualOverride) {
                        CheckpointSessionService.ACTION_STOP_MANUAL
                    } else {
                        CheckpointSessionService.ACTION_START_MANUAL
                    }
                    context.startService(Intent(context, CheckpointSessionService::class.java).setAction(action))
                },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) { Text(if (manualOverride) "Stop session" else "Start session") }

            Divider(modifier = Modifier.padding(vertical = 16.dp))

            Row(text = "Pause automatic prompts") {
                Switch(
                    checked = paused,
                    onCheckedChange = {
                        paused = it
                        Settings.setPaused(context, it)
                    },
                )
            }

            Row(text = "Auto-start on unlock") {
                Switch(
                    checked = autoStart,
                    onCheckedChange = {
                        autoStart = it
                        Settings.setAutoStartOnUnlockEnabled(context, it)
                    },
                )
            }

            Text("Prompt interval", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
            Column {
                listOf(15, 30, 60).forEach { minutes ->
                    Button(
                        onClick = {
                            intervalMinutes = minutes
                            Settings.setIntervalMinutes(context, minutes)
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    ) {
                        Text(if (intervalMinutes == minutes) "✓ Every $minutes minutes" else "Every $minutes minutes")
                    }
                }
            }

            Button(
                onClick = { CsvExporter.exportAndShare(context) },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) { Text("Export CSV") }

            Text("Today's log", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
            LazyColumn {
                items(entries) { entry ->
                    Text("${entry.durationMinutes}m — ${entry.activity}", modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun Row(text: String, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
    ) {
        Text(text)
        content()
    }
}
