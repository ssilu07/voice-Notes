package com.royals.voicenotes

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService

class NoteWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return NoteWidgetFactory(applicationContext)
    }
}

class NoteWidgetFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var notes: List<Note> = emptyList()

    override fun onCreate() {
        loadNotes()
    }

    override fun onDataSetChanged() {
        loadNotes()
    }

    private fun loadNotes() {
        // Access Room database synchronously (this runs on a binder thread)
        val db = NoteDatabase.getDatabase(context)
        val cursor = db.openHelper.readableDatabase.query(
            "SELECT id, title, content, timestamp, noteType, audioFilePath, isPinned, category, isArchived, reminderTime FROM notes WHERE isArchived = 0 ORDER BY isPinned DESC, id DESC LIMIT 10"
        )

        val loadedNotes = mutableListOf<Note>()
        while (cursor.moveToNext()) {
            val reminderTimeIndex = cursor.getColumnIndex("reminderTime")
            val reminderTime = if (reminderTimeIndex >= 0 && !cursor.isNull(reminderTimeIndex)) {
                cursor.getLong(reminderTimeIndex)
            } else null

            loadedNotes.add(
                Note(
                    id = cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                    title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
                    content = cursor.getString(cursor.getColumnIndexOrThrow("content")),
                    timestamp = cursor.getString(cursor.getColumnIndexOrThrow("timestamp")),
                    noteType = cursor.getString(cursor.getColumnIndexOrThrow("noteType")),
                    audioFilePath = cursor.getString(cursor.getColumnIndex("audioFilePath")),
                    isPinned = cursor.getInt(cursor.getColumnIndexOrThrow("isPinned")) == 1,
                    category = cursor.getString(cursor.getColumnIndexOrThrow("category")),
                    isArchived = cursor.getInt(cursor.getColumnIndexOrThrow("isArchived")) == 1,
                    reminderTime = reminderTime
                )
            )
        }
        cursor.close()
        notes = loadedNotes
    }

    override fun onDestroy() {
        notes = emptyList()
    }

    override fun getCount(): Int = notes.size

    override fun getViewAt(position: Int): RemoteViews {
        val note = notes[position]
        val views = RemoteViews(context.packageName, R.layout.widget_note_item)

        views.setTextViewText(R.id.tvWidgetNoteTitle, note.title)
        views.setTextViewText(R.id.tvWidgetNotePreview, note.content.take(80))

        // Set fill-in intent for item click
        val fillInIntent = Intent().apply {
            putExtra("noteId", note.id)
        }
        views.setOnClickFillInIntent(R.id.tvWidgetNoteTitle, fillInIntent)
        views.setOnClickFillInIntent(R.id.tvWidgetNotePreview, fillInIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = notes[position].id.toLong()
    override fun hasStableIds(): Boolean = true
}
