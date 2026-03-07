package com.royals.voicenotes

object TextAnalyzer {

    private val STOP_WORDS = setOf(
        "a", "an", "the", "is", "it", "in", "on", "at", "to", "for", "of", "and", "or",
        "but", "not", "with", "as", "by", "from", "that", "this", "was", "are", "be",
        "been", "being", "have", "has", "had", "do", "does", "did", "will", "would",
        "could", "should", "may", "might", "shall", "can", "need", "dare", "ought",
        "used", "i", "me", "my", "we", "our", "you", "your", "he", "him", "his",
        "she", "her", "they", "them", "their", "what", "which", "who", "whom",
        "so", "than", "too", "very", "just", "because", "if", "when", "where",
        "how", "all", "each", "every", "both", "few", "more", "most", "other",
        "some", "such", "no", "nor", "only", "own", "same", "also", "into",
        "about", "up", "out", "off", "over", "under", "again", "then", "once",
        "here", "there", "why", "am", "were", "its", "let", "us",
        // Hindi common stop words
        "ka", "ki", "ke", "ko", "hai", "hain", "tha", "thi", "the", "ye", "wo",
        "se", "me", "mein", "par", "pe", "aur", "ya", "nahi", "nhi", "bhi",
        "kya", "kab", "kaise", "kahan", "kaun", "jo", "jab", "jaise"
    )

    /**
     * Extractive summarization: scores sentences by word frequency and returns top N.
     */
    fun summarize(text: String, maxSentences: Int = 3): String {
        if (text.isBlank()) return "Nothing to summarize."

        val sentences = splitSentences(text)
        if (sentences.size <= maxSentences) return text.trim()

        // Build word frequency map (excluding stop words)
        val wordFreq = mutableMapOf<String, Int>()
        sentences.forEach { sentence ->
            extractWords(sentence).forEach { word ->
                if (word !in STOP_WORDS && word.length > 2) {
                    wordFreq[word] = (wordFreq[word] ?: 0) + 1
                }
            }
        }

        if (wordFreq.isEmpty()) return sentences.take(maxSentences).joinToString(". ") + "."

        val maxFreq = wordFreq.values.max()

        // Score each sentence
        val scored = sentences.mapIndexed { index, sentence ->
            val words = extractWords(sentence)
            val score = if (words.isEmpty()) 0.0 else {
                words.sumOf { word ->
                    (wordFreq[word] ?: 0).toDouble() / maxFreq
                } / words.size +
                    // Slight bonus for earlier sentences (positional bias)
                    (if (index == 0) 0.2 else 0.0)
            }
            IndexedValue(index, sentence) to score
        }

        // Pick top N sentences, return in original order
        return scored
            .sortedByDescending { it.second }
            .take(maxSentences)
            .sortedBy { it.first.index }
            .joinToString(". ") { it.first.value.trim() }
            .let { if (it.endsWith(".")) it else "$it." }
    }

    /**
     * Extract keywords/tags from text by word frequency (excluding stop words).
     */
    fun generateTags(text: String, maxTags: Int = 5): List<String> {
        if (text.isBlank()) return emptyList()

        val wordFreq = mutableMapOf<String, Int>()
        extractWords(text).forEach { word ->
            if (word !in STOP_WORDS && word.length > 2) {
                wordFreq[word] = (wordFreq[word] ?: 0) + 1
            }
        }

        return wordFreq.entries
            .sortedByDescending { it.value }
            .take(maxTags)
            .map { "#${it.key}" }
    }

    private fun splitSentences(text: String): List<String> {
        return text.split(Regex("[.!?]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun extractWords(text: String): List<String> {
        return text.lowercase()
            .replace(Regex("[^a-zA-Z0-9\\s]"), "")
            .split("\\s+".toRegex())
            .filter { it.isNotBlank() }
    }
}
