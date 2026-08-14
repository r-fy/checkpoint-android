package org.rfisolns.checkpoint

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

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

    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var checkingForUpdate by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun runUpdate(info: UpdateInfo) {
        scope.launch {
            updateStatus = "Downloading v${info.version}…"
            when (val result = UpdateChecker.downloadAndVerify(context, info)) {
                is DownloadResult.Success -> {
                    if (UpdateChecker.canInstall(context)) {
                        updateStatus = null
                        UpdateChecker.install(context, result.file)
                    } else {
                        updateStatus = "Enable \"Allow from this source\", then tap Update again."
                        UpdateChecker.requestInstallPermission(context)
                    }
                }
                DownloadResult.ChecksumMismatch -> updateStatus = "Download didn't match the published checksum. Try again."
                is DownloadResult.Error -> updateStatus = "Update failed: ${result.message}"
            }
        }
    }

    LaunchedEffect(Unit) {
        checkingForUpdate = true
        updateInfo = UpdateChecker.checkForUpdate(context)
        checkingForUpdate = false
    }

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

            updateInfo?.let { info ->
                Text(
                    "Update available: v${info.version}",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Button(
                    onClick = { runUpdate(info) },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) { Text("Update now") }
            }
            updateStatus?.let { status ->
                Text(status, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            }

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

            Button(
                onClick = {
                    scope.launch {
                        checkingForUpdate = true
                        val found = UpdateChecker.checkForUpdate(context)
                        updateInfo = found
                        updateStatus = if (found == null) "You're up to date." else null
                        checkingForUpdate = false
                    }
                },
                enabled = !checkingForUpdate,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text(if (checkingForUpdate) "Checking…" else "Check for updates") }

            Button(
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${UpdateChecker.REPO}/releases")),
                    )
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text("View on GitHub") }

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
