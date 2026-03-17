package com.royals.voicenotes

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent.getIntExtra(EXTRA_NOTE_ID, -1)
        val noteTitle = intent.getStringExtra(EXTRA_NOTE_TITLE) ?: "Note Reminder"
        val noteContent = intent.getStringExtra(EXTRA_NOTE_CONTENT) ?: ""

        if (noteId != -1) {
            NotificationHelper.showReminderNotification(context, noteId, noteTitle, noteContent)
        }
    }

    companion object {
        const val EXTRA_NOTE_ID = "extra_note_id"
        const val EXTRA_NOTE_TITLE = "extra_note_title"
        const val EXTRA_NOTE_CONTENT = "extra_note_content"
    }
}
