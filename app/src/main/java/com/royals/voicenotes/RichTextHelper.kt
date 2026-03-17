package com.royals.voicenotes

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.widget.EditText

/**
 * Helper for applying rich text formatting (bold, italic, underline, bullets)
 * to an EditText using Android's built-in Spannable system.
 */
object RichTextHelper {

    fun toggleBold(editText: EditText) {
        toggleStyle(editText, Typeface.BOLD)
    }

    fun toggleItalic(editText: EditText) {
        toggleStyle(editText, Typeface.ITALIC)
    }

    fun toggleUnderline(editText: EditText) {
        val start = editText.selectionStart
        val end = editText.selectionEnd
        if (start == end) return // No selection

        val spannable = editText.text as Spannable
        val existingSpans = spannable.getSpans(start, end, UnderlineSpan::class.java)

        if (existingSpans.isNotEmpty()) {
            // Remove underline
            for (span in existingSpans) {
                spannable.removeSpan(span)
            }
        } else {
            // Apply underline
            spannable.setSpan(
                UnderlineSpan(),
                start, end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    fun insertBulletList(editText: EditText) {
        val start = editText.selectionStart
        val text = editText.text

        // Find the start of the current line
        var lineStart = start
        while (lineStart > 0 && text[lineStart - 1] != '\n') {
            lineStart--
        }

        val currentLine = text.substring(lineStart, start)

        if (currentLine.startsWith("• ")) {
            // Remove bullet
            text.delete(lineStart, lineStart + 2)
        } else {
            // Add bullet
            text.insert(lineStart, "• ")
        }
    }

    private fun toggleStyle(editText: EditText, style: Int) {
        val start = editText.selectionStart
        val end = editText.selectionEnd
        if (start == end) return // No selection

        val spannable = editText.text as Spannable
        val existingSpans = spannable.getSpans(start, end, StyleSpan::class.java)

        val hasStyle = existingSpans.any { it.style == style }

        if (hasStyle) {
            // Remove the style
            for (span in existingSpans) {
                if (span.style == style) {
                    spannable.removeSpan(span)
                }
            }
        } else {
            // Apply the style
            spannable.setSpan(
                StyleSpan(style),
                start, end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    /**
     * Convert spannable text to simple HTML for storage.
     */
    fun toHtml(text: Spannable): String {
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            val boldSpans = text.getSpans(i, i + 1, StyleSpan::class.java).filter { it.style == Typeface.BOLD }
            val italicSpans = text.getSpans(i, i + 1, StyleSpan::class.java).filter { it.style == Typeface.ITALIC }
            val underlineSpans = text.getSpans(i, i + 1, UnderlineSpan::class.java)

            // Check if span starts here
            boldSpans.forEach { span ->
                if (text.getSpanStart(span) == i) sb.append("<b>")
            }
            italicSpans.forEach { span ->
                if (text.getSpanStart(span) == i) sb.append("<i>")
            }
            underlineSpans.forEach { span ->
                if (text.getSpanStart(span) == i) sb.append("<u>")
            }

            when (c) {
                '\n' -> sb.append("<br>")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '&' -> sb.append("&amp;")
                else -> sb.append(c)
            }

            // Check if span ends here
            underlineSpans.forEach { span ->
                if (text.getSpanEnd(span) == i + 1) sb.append("</u>")
            }
            italicSpans.forEach { span ->
                if (text.getSpanEnd(span) == i + 1) sb.append("</i>")
            }
            boldSpans.forEach { span ->
                if (text.getSpanEnd(span) == i + 1) sb.append("</b>")
            }

            i++
        }
        return sb.toString()
    }

    /**
     * Convert HTML back to SpannableStringBuilder for display.
     */
    fun fromHtml(html: String): SpannableStringBuilder {
        @Suppress("DEPRECATION")
        val spanned = android.text.Html.fromHtml(html)
        return SpannableStringBuilder(spanned)
    }

    /**
     * Strip HTML tags for plain text operations (search, export, etc.)
     */
    fun stripHtml(html: String): String {
        return html.replace(Regex("<[^>]*>"), "")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("<br>", "\n")
    }
}
