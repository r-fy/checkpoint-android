package org.rfisolns.checkpoint

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ponytail: framework DatePickerDialog/TimePickerDialog, not a Compose date
// picker — one field, chained imperative dialogs are less code than wiring
// up Material3 picker state for something this small.
@Composable
fun EditEntryDialog(
    context: Context,
    entry: ActivityEntry,
    onSave: (activity: String, durationMinutes: Int, timestamp: String) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    var activityText by remember { mutableStateOf(entry.activity) }
    var durationText by remember { mutableStateOf(entry.durationMinutes.toString()) }
    var zonedDateTime by remember { mutableStateOf(Instant.parse(entry.timestamp).atZone(ZoneId.systemDefault())) }
    val displayFormatter = remember { DateTimeFormatter.ofPattern("MMM d, h:mm a") }

    fun pickDateTime() {
        val current = zonedDateTime
        DatePickerDialog(
            context,
            { _, year, month, day ->
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        zonedDateTime = current
                            .withYear(year)
                            .withMonth(month + 1)
                            .withDayOfMonth(day)
                            .withHour(hour)
                            .withMinute(minute)
                            .withSecond(0)
                    },
                    current.hour,
                    current.minute,
                    false,
                ).show()
            },
            current.year,
            current.monthValue - 1,
            current.dayOfMonth,
        ).show()
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Edit entry") },
        text = {
            Column {
                OutlinedTextField(
                    value = activityText,
                    onValueChange = { activityText = it },
                    label = { Text("Activity") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = durationText,
                    onValueChange = { durationText = it.filter { c -> c.isDigit() } },
                    label = { Text("Minutes") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Button(
                    onClick = { pickDateTime() },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) { Text(zonedDateTime.format(displayFormatter)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val minutes = durationText.toIntOrNull() ?: entry.durationMinutes
                if (activityText.isNotBlank()) {
                    val timestamp = DateTimeFormatter.ISO_INSTANT.format(zonedDateTime.toInstant())
                    onSave(activityText.trim(), minutes, timestamp)
                }
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Delete") }
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        },
    )
}
