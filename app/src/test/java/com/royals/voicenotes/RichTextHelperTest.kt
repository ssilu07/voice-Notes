package com.royals.voicenotes

import org.junit.Assert.*
import org.junit.Test

class RichTextHelperTest {

    @Test
    fun stripHtml_removesBasicTags() {
        val html = "<b>Bold</b> and <i>italic</i>"
        val result = RichTextHelper.stripHtml(html)
        assertEquals("Bold and italic", result)
    }

    @Test
    fun stripHtml_handlesEmptyString() {
        assertEquals("", RichTextHelper.stripHtml(""))
    }

    @Test
    fun stripHtml_plainText_unchanged() {
        val text = "Hello world"
        assertEquals(text, RichTextHelper.stripHtml(text))
    }

    @Test
    fun stripHtml_handlesEntities() {
        val html = "5 &lt; 10 &amp; 10 &gt; 5"
        val result = RichTextHelper.stripHtml(html)
        assertEquals("5 < 10 & 10 > 5", result)
    }

    @Test
    fun stripHtml_nestedTags_stripped() {
        val html = "<b><i>Bold Italic</i></b>"
        val result = RichTextHelper.stripHtml(html)
        assertEquals("Bold Italic", result)
    }

    @Test
    fun stripHtml_underlineTags() {
        val html = "<u>Underlined text</u>"
        val result = RichTextHelper.stripHtml(html)
        assertEquals("Underlined text", result)
    }

    @Test
    fun stripHtml_brTags_convertedToNewline() {
        val html = "Line 1<br>Line 2"
        val result = RichTextHelper.stripHtml(html)
        assertEquals("Line 1\nLine 2", result)
    }

    @Test
    fun stripHtml_mixedContent() {
        val html = "<b>Title</b><br>• Item 1<br>• <i>Item 2</i>"
        val result = RichTextHelper.stripHtml(html)
        assertEquals("Title\n• Item 1\n• Item 2", result)
    }
}
