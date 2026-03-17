package com.royals.voicenotes

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.sqlite.db.SimpleSQLiteQuery

class NoteRepository(private val noteDao: NoteDao) {
    val allNotes: LiveData<List<Note>> = noteDao.getAllNotes()

    fun searchNotes(query: String): LiveData<List<Note>> {
        val trimmed = query.trim()
        // Use LIKE fallback for very short queries; FTS needs meaningful tokens
        if (trimmed.length < 2) {
            return noteDao.searchNotesLike(trimmed)
        }
        return try {
            // Build FTS query with prefix matching: "hel wor" -> "hel* wor*"
            val ftsQuery = trimmed.split("\\s+".toRegex()).joinToString(" ") { "$it*" }
            val sqlQuery = SimpleSQLiteQuery(
                "SELECT notes.* FROM notes JOIN notes_fts ON notes.id = notes_fts.docid WHERE notes.isArchived = 0 AND notes_fts MATCH ? ORDER BY notes.isPinned DESC, notes.id DESC",
                arrayOf(ftsQuery)
            )
            noteDao.searchNotesFts(sqlQuery)
        } catch (e: Exception) {
            Log.w("NoteRepository", "FTS search failed, falling back to LIKE", e)
            noteDao.searchNotesLike(trimmed)
        }
    }

    fun getNotesByCategory(category: String): LiveData<List<Note>> = noteDao.getNotesByCategory(category)

    fun getArchivedNotes(): LiveData<List<Note>> = noteDao.getArchivedNotes()

    fun getNotesWithActiveReminders(): LiveData<List<Note>> =
        noteDao.getNotesWithActiveReminders(System.currentTimeMillis())

    suspend fun insert(note: Note): Long = noteDao.insert(note)

    suspend fun update(note: Note): Int = noteDao.update(note)

    suspend fun delete(note: Note): Int = noteDao.delete(note)

    suspend fun deleteAll(): Int = noteDao.deleteAll()

    suspend fun getNotesCount(): Int = noteDao.getNotesCount()

    suspend fun getTotalWordCount(): Int = noteDao.getTotalWordCount() ?: 0

    suspend fun getNoteById(noteId: Int): Note? = noteDao.getNoteById(noteId)

    suspend fun updatePinStatus(noteId: Int, isPinned: Boolean) = noteDao.updatePinStatus(noteId, isPinned)

    suspend fun updateArchiveStatus(noteId: Int, isArchived: Boolean) = noteDao.updateArchiveStatus(noteId, isArchived)

    suspend fun updateReminderTime(noteId: Int, reminderTime: Long?) = noteDao.updateReminderTime(noteId, reminderTime)
}
