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
        val file = logFile(context)
        file.appendText(json.toString() + "\n")
        val appContext = context.applicationContext
        Thread { LogBackup.mirror(appContext, file) }.start()
        return entry
    }

    fun updateEntry(context: Context, id: String, activity: String, durationMinutes: Int, timestamp: String): Boolean {
        val all = readAll(context)
        val index = all.indexOfFirst { it.id == id }
        if (index == -1) return false
        val updated = all.toMutableList()
        updated[index] = ActivityEntry(id, activity, timestamp, durationMinutes)
        writeAll(context, updated)
        return true
    }

    fun deleteEntry(context: Context, id: String): Boolean {
        val all = readAll(context)
        if (all.none { it.id == id }) return false
        writeAll(context, all.filterNot { it.id == id })
        return true
    }

    private fun writeAll(context: Context, entries: List<ActivityEntry>) {
        val text = entries.joinToString("") { entry ->
            JSONObject()
                .put("id", entry.id)
                .put("activity", entry.activity)
                .put("timestamp", entry.timestamp)
                .put("durationMinutes", entry.durationMinutes)
                .toString() + "\n"
        }
        val file = logFile(context)
        file.writeText(text)
        val appContext = context.applicationContext
        Thread { LogBackup.mirror(appContext, file) }.start()
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
