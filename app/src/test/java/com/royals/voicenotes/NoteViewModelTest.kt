package com.royals.voicenotes

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for NoteViewModel logic that can be tested without Android framework.
 * Tests the observable state patterns and data transformation logic.
 */
class NoteViewModelTest {

    @Test
    fun event_singleConsumption_works() {
        val event = Event("navigate")
        assertEquals("navigate", event.getContentIfNotHandled())
        assertNull(event.getContentIfNotHandled())
    }

    @Test
    fun event_peekContent_doesNotConsume() {
        val event = Event(42)
        assertEquals(42, event.peekContent())
        assertEquals(42, event.getContentIfNotHandled())
        assertNull(event.getContentIfNotHandled())
    }

    @Test
    fun note_togglePin_copyWorks() {
        val note = Note(id = 1, title = "Test", content = "Content", timestamp = "now", isPinned = false)
        val toggled = note.copy(isPinned = !note.isPinned)
        assertTrue(toggled.isPinned)
        assertEquals(note.id, toggled.id)
    }

    @Test
    fun note_toggleArchive_copyWorks() {
        val note = Note(id = 1, title = "Test", content = "Content", timestamp = "now", isArchived = false)
        val archived = note.copy(isArchived = true)
        assertTrue(archived.isArchived)
        assertFalse(note.isArchived)
    }

    @Test
    fun note_withReminder_futureTime_hasReminder() {
        val futureTime = System.currentTimeMillis() + 60_000
        val note = Note(id = 1, title = "Test", content = "Content", timestamp = "now", reminderTime = futureTime)
        assertTrue(note.hasReminder())
    }

    @Test
    fun note_withReminder_pastTime_doesNotHaveReminder() {
        val pastTime = System.currentTimeMillis() - 60_000
        val note = Note(id = 1, title = "Test", content = "Content", timestamp = "now", reminderTime = pastTime)
        assertFalse(note.hasReminder())
    }

    @Test
    fun note_withNullReminder_doesNotHaveReminder() {
        val note = Note(id = 1, title = "Test", content = "Content", timestamp = "now", reminderTime = null)
        assertFalse(note.hasReminder())
    }

    @Test
    fun note_defaultArchiveStatus_isFalse() {
        val note = Note(id = 1, title = "Test", content = "Content", timestamp = "now")
        assertFalse(note.isArchived)
    }

    @Test
    fun note_defaultReminderTime_isNull() {
        val note = Note(id = 1, title = "Test", content = "Content", timestamp = "now")
        assertNull(note.reminderTime)
    }
}
