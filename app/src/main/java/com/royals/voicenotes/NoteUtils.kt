package com.royals.voicenotes

import android.content.Context
import android.util.Log
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.util.Locale

object NoteUtils {

    /**
     * Shows a delete confirmation dialog for a note with undo support.
     * Audio files are only deleted after the undo window closes, preventing orphaned files.
     */
    fun showDeleteConfirmation(
        context: Context,
        note: Note,
        noteViewModel: NoteViewModel,
        snackbarRoot: View,
        tag: String = "NoteUtils"
    ) {
        val noteType = if (note.isAudioNote()) "audio recording" else "note"
        val displayType = noteType.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }

        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.delete_dialog_title, displayType))
            .setMessage(context.getString(R.string.delete_dialog_message, note.title))
            .setPositiveButton(context.getString(R.string.action_delete)) { _, _ ->
                noteViewModel.delete(note)

                val snackbar = Snackbar.make(snackbarRoot, "$displayType deleted", Snackbar.LENGTH_LONG)
                    .setAction(context.getString(R.string.action_undo)) {
                        noteViewModel.insert(note.copy(id = 0))
                    }

                // Only delete audio file after snackbar dismisses WITHOUT undo
                snackbar.addCallback(object : Snackbar.Callback() {
                    override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                        if (event != DISMISS_EVENT_ACTION) {
                            deleteAudioFile(note, tag)
                        }
                    }
                })
                snackbar.show()
            }
            .setNegativeButton(context.getString(R.string.action_cancel), null)
            .show()
    }

    /**
     * Safely deletes the audio file associated with a note.
     */
    fun deleteAudioFile(note: Note, tag: String = "NoteUtils") {
        if (note.isAudioNote() && note.audioFilePath != null) {
            val audioFile = File(note.audioFilePath)
            if (audioFile.exists() && !audioFile.delete()) {
                Log.e(tag, "Failed to delete audio file: ${note.audioFilePath}")
            }
        }
    }
}
