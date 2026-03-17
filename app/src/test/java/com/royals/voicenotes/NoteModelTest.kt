package com.royals.voicenotes

import org.junit.Assert.*
import org.junit.Test

/**
 * Extended Note model tests covering new fields (archive, reminder).
 */
class NoteModelTest {

    @Test
    fun note_allCategories_containsFourItems() {
        assertEquals(4, Note.ALL_CATEGORIES.size)
        assertTrue(Note.ALL_CATEGORIES.contains(Note.CATEGORY_GENERAL))
        assertTrue(Note.ALL_CATEGORIES.contains(Note.CATEGORY_WORK))
        assertTrue(Note.ALL_CATEGORIES.contains(Note.CATEGORY_PERSONAL))
        assertTrue(Note.ALL_CATEGORIES.contains(Note.CATEGORY_IDEAS))
    }

    @Test
    fun note_copy_preservesAllFields() {
        val original = Note(
            id = 5,
            title = "My Note",
            content = "Some content",
            timestamp = "Jan 01, 2025",
            noteType = Note.TYPE_TEXT,
            audioFilePath = null,
            isPinned = true,
            category = Note.CATEGORY_WORK,
            isArchived = false,
            reminderTime = 1234567890L
        )
        val copy = original.copy()
        assertEquals(original, copy)
    }

    @Test
    fun note_copy_modifySingleField() {
        val note = Note(id = 1, title = "T", content = "C", timestamp = "now", isPinned = false, isArchived = false)
        val archived = note.copy(isArchived = true)
        assertTrue(archived.isArchived)
        assertFalse(archived.isPinned)
        assertEquals("T", archived.title)
    }

    @Test
    fun note_audioNote_withFilePath() {
        val note = Note(
            id = 1, title = "Audio", content = "", timestamp = "now",
            noteType = Note.TYPE_AUDIO, audioFilePath = "/path/to/audio.m4a"
        )
        assertTrue(note.isAudioNote())
        assertFalse(note.isTextNote())
        assertEquals("/path/to/audio.m4a", note.audioFilePath)
    }

    @Test
    fun note_equality_differentIds_notEqual() {
        val note1 = Note(id = 1, title = "T", content = "C", timestamp = "now")
        val note2 = Note(id = 2, title = "T", content = "C", timestamp = "now")
        assertNotEquals(note1, note2)
    }

    @Test
    fun note_equality_sameData_equal() {
        val note1 = Note(id = 1, title = "T", content = "C", timestamp = "now")
        val note2 = Note(id = 1, title = "T", content = "C", timestamp = "now")
        assertEquals(note1, note2)
    }

    @Test
    fun note_hashCode_sameForEqualNotes() {
        val note1 = Note(id = 1, title = "T", content = "C", timestamp = "now")
        val note2 = Note(id = 1, title = "T", content = "C", timestamp = "now")
        assertEquals(note1.hashCode(), note2.hashCode())
    }

    @Test
    fun note_typeConstants_correctValues() {
        assertEquals("text", Note.TYPE_TEXT)
        assertEquals("audio", Note.TYPE_AUDIO)
    }

    @Test
    fun note_hasReminder_withZeroTime_noReminder() {
        // reminderTime = 0 is in the past
        val note = Note(id = 1, title = "T", content = "C", timestamp = "now", reminderTime = 0L)
        assertFalse(note.hasReminder())
    }

    @Test
    fun note_withReminderAndArchived() {
        val futureTime = System.currentTimeMillis() + 100_000
        val note = Note(
            id = 1, title = "T", content = "C", timestamp = "now",
            isArchived = true, reminderTime = futureTime
        )
        assertTrue(note.isArchived)
        assertTrue(note.hasReminder())
    }
}
