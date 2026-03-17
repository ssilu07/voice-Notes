package com.royals.voicenotes

import org.junit.Assert.*
import org.junit.Test

class TextAnalyzerTest {

    // --- summarize() tests ---

    @Test
    fun summarize_blankText_returnsDefault() {
        assertEquals("Nothing to summarize.", TextAnalyzer.summarize(""))
        assertEquals("Nothing to summarize.", TextAnalyzer.summarize("   "))
    }

    @Test
    fun summarize_shortText_returnsOriginal() {
        val text = "This is a short note. It has two sentences."
        assertEquals(text.trim(), TextAnalyzer.summarize(text, maxSentences = 3))
    }

    @Test
    fun summarize_longText_returnsSummary() {
        val text = "Kotlin is a modern programming language. " +
                "It runs on the JVM. " +
                "Android uses Kotlin as its primary language. " +
                "Kotlin is concise and safe. " +
                "Many developers prefer Kotlin over Java."

        val summary = TextAnalyzer.summarize(text, maxSentences = 2)

        // Summary should be shorter than original
        assertTrue(summary.length < text.length)
        // Summary should end with a period
        assertTrue(summary.endsWith("."))
        // Summary should contain Kotlin (the most frequent word)
        assertTrue(summary.contains("Kotlin", ignoreCase = true))
    }

    @Test
    fun summarize_respectsMaxSentences() {
        val text = "First sentence. Second sentence. Third sentence. Fourth sentence. Fifth sentence."
        val summary = TextAnalyzer.summarize(text, maxSentences = 2)
        // Should have at most 2 sentences (split by ". ")
        val sentenceCount = summary.split(". ").size
        assertTrue(sentenceCount <= 2)
    }

    @Test
    fun summarize_onlyStopWords_returnsFallback() {
        val text = "I am the one. He is in it. We are to be."
        val result = TextAnalyzer.summarize(text, maxSentences = 1)
        // Should still return something meaningful
        assertTrue(result.isNotBlank())
    }

    // --- generateTags() tests ---

    @Test
    fun generateTags_blankText_returnsEmpty() {
        assertEquals(emptyList<String>(), TextAnalyzer.generateTags(""))
        assertEquals(emptyList<String>(), TextAnalyzer.generateTags("   "))
    }

    @Test
    fun generateTags_normalText_returnsTags() {
        val text = "Android development with Kotlin is great. Kotlin makes Android development faster."
        val tags = TextAnalyzer.generateTags(text)

        assertTrue(tags.isNotEmpty())
        // Tags should start with #
        tags.forEach { assertTrue(it.startsWith("#")) }
        // Most frequent words should be in tags
        assertTrue(tags.any { it.contains("kotlin", ignoreCase = true) })
        assertTrue(tags.any { it.contains("android", ignoreCase = true) })
    }

    @Test
    fun generateTags_respectsMaxTags() {
        val text = "Alpha beta gamma delta epsilon zeta eta theta iota kappa lambda"
        val tags = TextAnalyzer.generateTags(text, maxTags = 3)
        assertTrue(tags.size <= 3)
    }

    @Test
    fun generateTags_filtersStopWords() {
        val text = "The quick brown fox jumps over the lazy dog"
        val tags = TextAnalyzer.generateTags(text)
        // "the" and "over" are stop words, should not appear
        tags.forEach { tag ->
            assertFalse(tag.equals("#the", ignoreCase = true))
            assertFalse(tag.equals("#over", ignoreCase = true))
        }
    }

    @Test
    fun generateTags_filtersShortWords() {
        val text = "Go do it an ox"
        val tags = TextAnalyzer.generateTags(text)
        // All words are <= 2 chars, should be filtered
        assertTrue(tags.isEmpty())
    }

    @Test
    fun generateTags_hindiStopWords_filtered() {
        val text = "hai aur bhi programming kotlin android development"
        val tags = TextAnalyzer.generateTags(text)
        tags.forEach { tag ->
            assertFalse(tag.equals("#hai", ignoreCase = true))
            assertFalse(tag.equals("#aur", ignoreCase = true))
            assertFalse(tag.equals("#bhi", ignoreCase = true))
        }
    }
}
