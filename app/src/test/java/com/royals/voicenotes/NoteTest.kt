package com.royals.voicenotes

import org.junit.Assert.*
import org.junit.Test

class NoteTest {

    @Test
    fun isTextNote_defaultType_returnsTrue() {
        val note = Note(id = 1, title = "Test", content = "Content", timestamp = "now")
        assertTrue(note.isTextNote())
    }

    @Test
    fun isAudioNote_audioType_returnsTrue() {
        val note = Note(
            id = 1, title = "Test", content = "Content",
            timestamp = "now", noteType = Note.TYPE_AUDIO
        )
        assertTrue(note.isAudioNote())
        assertFalse(note.isTextNote())
    }

    @Test
    fun isTextNote_audioType_returnsFalse() {
        val note = Note(
            id = 1, title = "Test", content = "Content",
            timestamp = "now", noteType = Note.TYPE_AUDIO
        )
        assertFalse(note.isTextNote())
    }

    @Test
    fun defaultValues_areCorrect() {
        val note = Note(id = 0, title = "Test", content = "Content", timestamp = "now")
        assertEquals(Note.TYPE_TEXT, note.noteType)
        assertNull(note.audioFilePath)
        assertFalse(note.isPinned)
        assertEquals(Note.CATEGORY_GENERAL, note.category)
        assertFalse(note.isArchived)
        assertNull(note.reminderTime)
    }

    @Test
    fun copy_withPinToggle_works() {
        val note = Note(id = 1, title = "Test", content = "Content", timestamp = "now", isPinned = false)
        val pinned = note.copy(isPinned = true)
        assertTrue(pinned.isPinned)
        assertEquals(note.id, pinned.id)
        assertEquals(note.title, pinned.title)
    }

    @Test
    fun categories_areCorrectValues() {
        assertEquals("general", Note.CATEGORY_GENERAL)
        assertEquals("work", Note.CATEGORY_WORK)
        assertEquals("personal", Note.CATEGORY_PERSONAL)
        assertEquals("ideas", Note.CATEGORY_IDEAS)
    }

    @Test
    fun hasReminder_withFutureTime_returnsTrue() {
        val futureTime = System.currentTimeMillis() + 3600_000
        val note = Note(id = 1, title = "T", content = "C", timestamp = "now", reminderTime = futureTime)
        assertTrue(note.hasReminder())
    }

    @Test
    fun hasReminder_withPastTime_returnsFalse() {
        val pastTime = System.currentTimeMillis() - 3600_000
        val note = Note(id = 1, title = "T", content = "C", timestamp = "now", reminderTime = pastTime)
        assertFalse(note.hasReminder())
    }

    @Test
    fun hasReminder_withNull_returnsFalse() {
        val note = Note(id = 1, title = "T", content = "C", timestamp = "now")
        assertFalse(note.hasReminder())
    }

    @Test
    fun copy_withArchive_works() {
        val note = Note(id = 1, title = "T", content = "C", timestamp = "now")
        val archived = note.copy(isArchived = true)
        assertTrue(archived.isArchived)
        assertFalse(note.isArchived)
    }
}
