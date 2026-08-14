package org.rfisolns.checkpoint

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File

// ponytail: mirrors the raw log file into the public Downloads folder via
// MediaStore on every write. Uninstalling the app wipes its private storage
// (including the primary log) but never touches Downloads, so this is what
// actually protects entries nobody remembered to export — not a reminder,
// an automatic copy that survives the uninstall that would otherwise eat it.
object LogBackup {

    fun mirror(context: Context, sourceFile: File) {
        if (!sourceFile.exists()) return
        val displayName = "checkpoint-${Settings.getDeviceLabel(context)}-backup.jsonl"
        val resolver = context.contentResolver

        val uri = findExisting(context, displayName) ?: resolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                put(MediaStore.Downloads.MIME_TYPE, "application/jsonl")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            },
        ) ?: return

        resolver.openOutputStream(uri, "wt")?.use { output ->
            sourceFile.inputStream().use { input -> input.copyTo(output) }
        }
    }

    private fun findExisting(context: Context, displayName: String): Uri? {
        val projection = arrayOf(MediaStore.Downloads._ID)
        val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ?"
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            arrayOf(displayName),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                return ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
            }
        }
        return null
    }
}
