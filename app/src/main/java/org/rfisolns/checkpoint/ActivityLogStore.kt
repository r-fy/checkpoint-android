package org.rfisolns.checkpoint

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

data class ActivityEntry(
    val id: String,
    val activity: String,
    val timestamp: String,
    val durationMinutes: Int,
)

// ponytail: JSONL parsed with org.json (built into Android), no serialization library needed.
object ActivityLogStore {

    private fun logFile(context: Context): File {
        val deviceLabel = Settings.getDeviceLabel(context)
        return File(context.getExternalFilesDir(null), "checkpoint-$deviceLabel.jsonl")
    }

    fun appendEntry(context: Context, activity: String, durationMinutes: Int): ActivityEntry {
        val entry = ActivityEntry(
            id = UUID.randomUUID().toString().uppercase(),
            activity = activity,
            timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now().truncatedTo(ChronoUnit.SECONDS)),
            durationMinutes = durationMinutes,
        )
        val json = JSONObject()
            .put("id", entry.id)
            .put("activity", entry.activity)
            .put("timestamp", entry.timestamp)
            .put("durationMinutes", entry.durationMinutes)
        logFile(context).appendText(json.toString() + "\n")
        return entry
    }

    fun readAll(context: Context): List<ActivityEntry> {
        val file = logFile(context)
        if (!file.exists()) return emptyList()
        return file.readLines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                runCatching {
                    val obj = JSONObject(line)
                    ActivityEntry(
                        id = obj.getString("id"),
                        activity = obj.getString("activity"),
                        timestamp = obj.getString("timestamp"),
                        durationMinutes = obj.getInt("durationMinutes"),
                    )
                }.getOrNull()
            }
    }

    fun todayEntries(context: Context): List<ActivityEntry> {
        val today = Instant.now().atZone(ZoneId.systemDefault()).toLocalDate()
        return readAll(context).filter { entry ->
            runCatching {
                Instant.parse(entry.timestamp).atZone(ZoneId.systemDefault()).toLocalDate() == today
            }.getOrDefault(false)
        }
    }

    fun filePath(context: Context): String = logFile(context).absolutePath
}
