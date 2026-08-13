package org.rfisolns.checkpoint

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

// ponytail: CSV only, not XLSX — opens fine in Sheets/Excel/Numbers and needs
// no extra library. Add a real XLSX writer if a spreadsheet-only workflow needs it.
object CsvExporter {

    fun exportAndShare(context: Context) {
        val entries = ActivityLogStore.readAll(context)
        val csv = buildString {
            appendLine("id,activity,timestamp,durationMinutes")
            for (entry in entries) {
                val escapedActivity = "\"" + entry.activity.replace("\"", "\"\"") + "\""
                appendLine("${entry.id},$escapedActivity,${entry.timestamp},${entry.durationMinutes}")
            }
        }
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportDir, "checkpoint-${Settings.getDeviceLabel(context)}.csv")
        file.writeText(csv)

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(shareIntent, "Export Checkpoint log").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }
}
