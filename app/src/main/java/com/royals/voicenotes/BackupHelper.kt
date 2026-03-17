package com.royals.voicenotes

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackupHelper {

    private const val TAG = "BackupHelper"

    fun exportBackup(context: Context, notes: List<Note>): Boolean {
        return try {
            val jsonArray = JSONArray()
            notes.forEach { note ->
                val obj = JSONObject().apply {
                    put("title", note.title)
                    put("content", note.content)
                    put("timestamp", note.timestamp)
                    put("noteType", note.noteType)
                    put("audioFilePath", note.audioFilePath ?: "")
                    put("isPinned", note.isPinned)
                    put("category", note.category)
                }
                jsonArray.put(obj)
            }

            val backupJson = JSONObject().apply {
                put("version", 1)
                put("exportDate", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
                put("notesCount", notes.size)
                put("notes", jsonArray)
            }

            val fileName = "voicenotes_backup_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.json"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { os ->
                        os.write(backupJson.toString(2).toByteArray())
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, fileName)
                file.writeText(backupJson.toString(2))
            }

            Log.i(TAG, "Backup created: $fileName with ${notes.size} notes")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create backup", e)
            false
        }
    }

    fun parseBackup(inputStream: InputStream): List<Note>? {
        return try {
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            val jsonArray = jsonObject.getJSONArray("notes")

            val notes = mutableListOf<Note>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val note = Note(
                    id = 0, // New ID will be auto-generated
                    title = obj.getString("title"),
                    content = obj.getString("content"),
                    timestamp = obj.getString("timestamp"),
                    noteType = obj.optString("noteType", Note.TYPE_TEXT),
                    audioFilePath = obj.optString("audioFilePath", "").ifEmpty { null },
                    isPinned = obj.optBoolean("isPinned", false),
                    category = obj.optString("category", Note.CATEGORY_GENERAL)
                )
                notes.add(note)
            }

            Log.i(TAG, "Parsed ${notes.size} notes from backup")
            notes
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse backup", e)
            null
        }
    }
}
