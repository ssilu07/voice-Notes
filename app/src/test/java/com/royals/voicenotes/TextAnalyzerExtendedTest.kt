package com.royals.voicenotes

import org.junit.Assert.*
import org.junit.Test

/**
 * Extended tests for TextAnalyzer covering edge cases and additional scenarios.
 */
class TextAnalyzerExtendedTest {

    // --- summarize() edge cases ---

    @Test
    fun summarize_singleSentence_returnsOriginal() {
        val text = "This is a single sentence without any period at the end"
        val result = TextAnalyzer.summarize(text)
        assertEquals(text.trim(), result)
    }

    @Test
    fun summarize_withExclamationAndQuestion_splitsSentences() {
        val text = "What is Kotlin? It is a language! Many use it. For Android development. Google loves Kotlin."
        val summary = TextAnalyzer.summarize(text, maxSentences = 2)
        assertTrue(summary.isNotBlank())
        assertTrue(summary.length <= text.length)
    }

    @Test
    fun summarize_repeatedWord_scores_highForRepeatedWordSentence() {
        val text = "Kotlin is amazing. Java is old. Kotlin development is fast. Kotlin code is clean. Other tools exist."
        val summary = TextAnalyzer.summarize(text, maxSentences = 2)
        // "Kotlin" appears 3 times, so sentences with Kotlin should score higher
        assertTrue(summary.contains("Kotlin", ignoreCase = true))
    }

    @Test
    fun summarize_maxSentences_one() {
        val text = "First sentence. Second sentence. Third sentence."
        val summary = TextAnalyzer.summarize(text, maxSentences = 1)
        // Should return only one sentence
        assertFalse(summary.contains(". "))
    }

    @Test
    fun summarize_veryLongText() {
        val sentences = (1..50).map { "This is sentence number $it with some content" }
        val text = sentences.joinToString(". ") + "."
        val summary = TextAnalyzer.summarize(text, maxSentences = 3)
        assertTrue(summary.isNotBlank())
        assertTrue(summary.length < text.length)
    }

    @Test
    fun summarize_specialCharacters_handled() {
        val text = "C++ is a language! C# is another. Python & Java are popular. Kotlin's syntax is clean."
        val summary = TextAnalyzer.summarize(text, maxSentences = 2)
        assertTrue(summary.isNotBlank())
    }

    // --- generateTags() edge cases ---

    @Test
    fun generateTags_singleWord_tooShort() {
        val text = "Hi"
        val tags = TextAnalyzer.generateTags(text)
        assertTrue(tags.isEmpty()) // "Hi" is only 2 chars
    }

    @Test
    fun generateTags_singleLongWord() {
        val text = "programming"
        val tags = TextAnalyzer.generateTags(text)
        assertEquals(1, tags.size)
        assertEquals("#programming", tags[0])
    }

    @Test
    fun generateTags_duplicateWords_countedCorrectly() {
        val text = "kotlin kotlin kotlin java java python"
        val tags = TextAnalyzer.generateTags(text, maxTags = 2)
        assertEquals(2, tags.size)
        assertEquals("#kotlin", tags[0]) // highest frequency
        assertEquals("#java", tags[1])
    }

    @Test
    fun generateTags_caseInsensitive() {
        val text = "Kotlin KOTLIN kotlin android Android ANDROID"
        val tags = TextAnalyzer.generateTags(text, maxTags = 2)
        assertEquals(2, tags.size)
        // Both should be present (lowercase)
        assertTrue(tags.contains("#kotlin"))
        assertTrue(tags.contains("#android"))
    }

    @Test
    fun generateTags_numbersIncluded() {
        val text = "version 123 update 456 version version"
        val tags = TextAnalyzer.generateTags(text, maxTags = 3)
        assertTrue(tags.any { it.contains("version") })
    }

    @Test
    fun generateTags_allStopWords_empty() {
        val text = "the and or but not for with"
        val tags = TextAnalyzer.generateTags(text)
        assertTrue(tags.isEmpty())
    }

    @Test
    fun generateTags_maxTags_zero() {
        val text = "kotlin android development programming"
        val tags = TextAnalyzer.generateTags(text, maxTags = 0)
        assertTrue(tags.isEmpty())
    }

    @Test
    fun generateTags_punctuation_stripped() {
        val text = "Hello, world! Programming: is great. Code (clean) works."
        val tags = TextAnalyzer.generateTags(text)
        tags.forEach { tag ->
            assertFalse(tag.contains(","))
            assertFalse(tag.contains("!"))
            assertFalse(tag.contains(":"))
            assertFalse(tag.contains("("))
        }
    }
}
