package com.royals.voicenotes

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Reschedules all active reminders after device reboot.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.d("BootReceiver", "Device booted, rescheduling reminders")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = NoteDatabase.getDatabase(context)
                val dao = db.noteDao()
                // Get all notes with future reminders directly via a synchronous query
                val currentTime = System.currentTimeMillis()
                val notes = dao.getNoteById(0) // We need a different approach

                // Since we can't easily get all reminder notes synchronously,
                // we'll use a raw query approach
                val cursor = db.openHelper.readableDatabase.query(
                    "SELECT * FROM notes WHERE reminderTime IS NOT NULL AND reminderTime > ?",
                    arrayOf(currentTime.toString())
                )

                while (cursor.moveToNext()) {
                    val id = cursor.getInt(cursor.getColumnIndexOrThrow("id"))
                    val title = cursor.getString(cursor.getColumnIndexOrThrow("title"))
                    val content = cursor.getString(cursor.getColumnIndexOrThrow("content"))
                    val reminderTime = cursor.getLong(cursor.getColumnIndexOrThrow("reminderTime"))

                    val note = Note(
                        id = id,
                        title = title,
                        content = content,
                        timestamp = "",
                        reminderTime = reminderTime
                    )
                    ReminderHelper.scheduleReminder(context, note, reminderTime)
                    Log.d("BootReceiver", "Rescheduled reminder for note $id")
                }
                cursor.close()
            } catch (e: Exception) {
                Log.e("BootReceiver", "Error rescheduling reminders", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
