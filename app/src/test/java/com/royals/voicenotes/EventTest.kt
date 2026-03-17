package com.royals.voicenotes

import org.junit.Assert.*
import org.junit.Test

class EventTest {

    @Test
    fun getContentIfNotHandled_firstCall_returnsContent() {
        val event = Event("test")
        assertEquals("test", event.getContentIfNotHandled())
    }

    @Test
    fun getContentIfNotHandled_secondCall_returnsNull() {
        val event = Event("test")
        event.getContentIfNotHandled() // first call
        assertNull(event.getContentIfNotHandled()) // second call
    }

    @Test
    fun hasBeenHandled_initiallyFalse() {
        val event = Event("test")
        assertFalse(event.hasBeenHandled)
    }

    @Test
    fun hasBeenHandled_trueAfterHandled() {
        val event = Event("test")
        event.getContentIfNotHandled()
        assertTrue(event.hasBeenHandled)
    }

    @Test
    fun peekContent_alwaysReturnsContent() {
        val event = Event("test")
        event.getContentIfNotHandled() // consume it
        assertEquals("test", event.peekContent()) // still available via peek
    }

    @Test
    fun peekContent_doesNotMarkAsHandled() {
        val event = Event("test")
        event.peekContent()
        assertFalse(event.hasBeenHandled)
        assertEquals("test", event.getContentIfNotHandled()) // still available
    }
}
