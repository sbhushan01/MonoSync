package com.monosync.model

/**
 * Represents a single line from a synchronized LRC lyrics file.
 *
 * @param timestampMs The timestamp in milliseconds when this line should be displayed.
 * @param text The lyric text content for this line.
 */
data class LyricLine(
    val timestampMs: Long,
    val text: String
)

/**
 * Parses a standard LRC-formatted string into a sorted list of [LyricLine] objects.
 *
 * Supports the standard LRC tag format: `[mm:ss.xx]` where `xx` can be centiseconds (2 digits)
 * or milliseconds (3 digits). Lines with multiple timestamps (e.g. `[01:23.45][01:28.90] text`)
 * are expanded into separate entries.
 *
 * Blank lyric lines are preserved as empty strings (common for instrumental breaks).
 * Metadata tags like `[ar:Artist]`, `[ti:Title]`, etc. are ignored.
 *
 * @param lrcString The raw LRC string, typically multi-line.
 * @return A list of [LyricLine] sorted by [LyricLine.timestampMs].
 *         Returns an empty list if [lrcString] is null or contains no valid timestamps.
 *
 * Example input:
 * ```
 * [00:12.34] First line of lyrics
 * [00:15.50] Second line
 * [01:02.123] Line with millisecond precision
 * ```
 */
fun parseLrc(lrcString: String?): List<LyricLine> {
    if (lrcString.isNullOrBlank()) return emptyList()

    // Matches [mm:ss.xx] or [mm:ss.xxx] timestamps
    val timestampPattern = Regex("""\[(\d{1,3}):(\d{2})\.(\d{2,3})]""")
    // Metadata tags like [ar:...], [ti:...], [al:...], [by:...], [offset:...]
    val metadataPattern = Regex("""\[[a-zA-Z]+:.*]""")

    val result = mutableListOf<LyricLine>()

    for (rawLine in lrcString.lines()) {
        val line = rawLine.trim()
        if (line.isEmpty()) continue

        // Skip pure metadata lines
        if (metadataPattern.matches(line) && !timestampPattern.containsMatchIn(line)) continue

        // Find all timestamps on this line
        val timestamps = timestampPattern.findAll(line).toList()
        if (timestamps.isEmpty()) continue

        // Extract the text portion after the last timestamp tag
        val lastMatch = timestamps.last()
        val text = line.substring(lastMatch.range.last + 1).trim()

        // Create a LyricLine for each timestamp (handles multi-timestamp lines)
        for (match in timestamps) {
            val minutes = match.groupValues[1].toLongOrNull() ?: continue
            val seconds = match.groupValues[2].toLongOrNull() ?: continue
            val fractional = match.groupValues[3]

            // Normalize to milliseconds: "34" → 340ms (centiseconds), "123" → 123ms
            val ms = if (fractional.length == 2) {
                fractional.toLongOrNull()?.times(10) ?: continue
            } else {
                fractional.toLongOrNull() ?: continue
            }

            val totalMs = (minutes * 60_000) + (seconds * 1_000) + ms
            result.add(LyricLine(timestampMs = totalMs, text = text))
        }
    }

    return result.sortedBy { it.timestampMs }
}

/**
 * Finds the index of the currently active lyric line based on playback position.
 *
 * Uses binary search for efficient O(log n) lookup. Returns the index of the last line
 * whose timestamp is ≤ [positionMs], or -1 if no line has started yet.
 *
 * @param lyrics The sorted list of [LyricLine] objects.
 * @param positionMs The current playback position in milliseconds.
 * @return The index of the active lyric line, or -1 if playback hasn't reached the first line.
 */
fun findActiveLyricIndex(lyrics: List<LyricLine>, positionMs: Long): Int {
    if (lyrics.isEmpty()) return -1

    var low = 0
    var high = lyrics.size - 1
    var result = -1

    while (low <= high) {
        val mid = (low + high) / 2
        if (lyrics[mid].timestampMs <= positionMs) {
            result = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }

    return result
}
