package org.rfisolns.checkpoint

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class QuickEntryActivity : ComponentActivity() {

    companion object {
        const val EXTRA_DURATION_MINUTES = "duration_minutes"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val defaultDuration = intent.getIntExtra(EXTRA_DURATION_MINUTES, 30)
        setContent {
            MaterialTheme {
                Surface {
                    QuickEntryScreen(
                        defaultDurationMinutes = defaultDuration,
                        onSave = { activityText, minutes ->
                            ActivityLogStore.appendEntry(this, activityText, minutes)
                            finish()
                        },
                        onCancel = { finish() },
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickEntryScreen(
    defaultDurationMinutes: Int,
    onSave: (String, Int) -> Unit,
    onCancel: () -> Unit,
) {
    var activityText by remember { mutableStateOf("") }
    var durationText by remember { mutableStateOf(defaultDurationMinutes.toString()) }

    Column(modifier = Modifier.padding(24.dp)) {
        Text("What have you been doing?", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = activityText,
            onValueChange = { activityText = it },
            label = { Text("Activity") },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        )
        OutlinedTextField(
            value = durationText,
            onValueChange = { durationText = it.filter { c -> c.isDigit() } },
            label = { Text("Minutes") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Column(modifier = Modifier.padding(top = 16.dp)) {
            Button(
                onClick = {
                    val minutes = durationText.toIntOrNull() ?: defaultDurationMinutes
                    if (activityText.isNotBlank()) onSave(activityText.trim(), minutes)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
            Button(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text("Cancel") }
        }
    }
}
