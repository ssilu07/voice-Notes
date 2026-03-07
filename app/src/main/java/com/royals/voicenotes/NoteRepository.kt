package com.royals.voicenotes

import androidx.lifecycle.LiveData

class NoteRepository(private val noteDao: NoteDao) {
    val allNotes: LiveData<List<Note>> = noteDao.getAllNotes()

    fun searchNotes(query: String): LiveData<List<Note>> = noteDao.searchNotes(query)

    suspend fun insert(note: Note): Long = noteDao.insert(note)

    suspend fun update(note: Note): Int = noteDao.update(note)

    suspend fun delete(note: Note): Int = noteDao.delete(note)

    suspend fun deleteAll(): Int = noteDao.deleteAll()

    suspend fun getNotesCount(): Int = noteDao.getNotesCount()

    suspend fun getTotalWordCount(): Int = noteDao.getTotalWordCount() ?: 0

    suspend fun getNoteById(noteId: Int): Note? = noteDao.getNoteById(noteId)

    suspend fun updatePinStatus(noteId: Int, isPinned: Boolean) = noteDao.updatePinStatus(noteId, isPinned)
}
