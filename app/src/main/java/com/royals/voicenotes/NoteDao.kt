package com.royals.voicenotes

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE isArchived = 0 ORDER BY isPinned DESC, id DESC")
    fun getAllNotes(): LiveData<List<Note>>

    // LIKE-based fallback for very short queries (< 2 chars) where FTS won't help
    @Query("SELECT * FROM notes WHERE isArchived = 0 AND (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%') ORDER BY isPinned DESC, id DESC")
    fun searchNotesLike(query: String): LiveData<List<Note>>

    // FTS4-powered full-text search for fast matching
    @RawQuery(observedEntities = [Note::class])
    fun searchNotesFts(query: SupportSQLiteQuery): LiveData<List<Note>>

    @Query("SELECT * FROM notes WHERE isArchived = 0 AND category = :category ORDER BY isPinned DESC, id DESC")
    fun getNotesByCategory(category: String): LiveData<List<Note>>

    // Archive queries
    @Query("SELECT * FROM notes WHERE isArchived = 1 ORDER BY id DESC")
    fun getArchivedNotes(): LiveData<List<Note>>

    @Query("UPDATE notes SET isArchived = :isArchived WHERE id = :noteId")
    suspend fun updateArchiveStatus(noteId: Int, isArchived: Boolean)

    // Reminder queries
    @Query("UPDATE notes SET reminderTime = :reminderTime WHERE id = :noteId")
    suspend fun updateReminderTime(noteId: Int, reminderTime: Long?)

    @Query("SELECT * FROM notes WHERE reminderTime IS NOT NULL AND reminderTime > :currentTime ORDER BY reminderTime ASC")
    fun getNotesWithActiveReminders(currentTime: Long): LiveData<List<Note>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: Note): Long

    @Update
    suspend fun update(note: Note): Int

    @Delete
    suspend fun delete(note: Note): Int

    @Query("DELETE FROM notes")
    suspend fun deleteAll(): Int

    @Query("SELECT COUNT(*) FROM notes WHERE isArchived = 0")
    suspend fun getNotesCount(): Int

    @Query("SELECT SUM(LENGTH(TRIM(content)) - LENGTH(REPLACE(TRIM(content), ' ', '')) + 1) FROM notes WHERE isArchived = 0 AND TRIM(content) != ''")
    suspend fun getTotalWordCount(): Int?

    @Query("SELECT * FROM notes WHERE id = :noteId")
    suspend fun getNoteById(noteId: Int): Note?

    @Query("UPDATE notes SET isPinned = :isPinned WHERE id = :noteId")
    suspend fun updatePinStatus(noteId: Int, isPinned: Boolean)
}
