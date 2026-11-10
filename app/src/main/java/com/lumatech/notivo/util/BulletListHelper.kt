package com.lumatech.notivo.util

/**
 * Pure text transformations for bulleted lists (`• `) supporting dot-aware indentation
 * and Enter-key continuation/outdenting.
 */
object BulletListHelper {

    const val BULLET_CHAR: Char = '\u2022'
    const val BULLET_PREFIX: String = "• "
    const val INDENT_UNIT: String = "    "

    data class ParsedBulletLine(
        val indentSpaces: Int,
        val hasBullet: Boolean,
        val body: String
    ) {
        val linePrefix: String
            get() = if (hasBullet) "${" ".repeat(indentSpaces)}$BULLET_PREFIX" else ""
    }

    data class TextEditResult(
        val text: String,
        val cursorPosition: Int
    )

    fun parseLine(line: String): ParsedBulletLine {
        var leadingSpaces = 0
        while (leadingSpaces < line.length && line[leadingSpaces] == ' ') {
            leadingSpaces++
        }
        val remainder = line.substring(leadingSpaces)
        return when {
            remainder.startsWith(BULLET_PREFIX) -> ParsedBulletLine(
                indentSpaces = leadingSpaces,
                hasBullet = true,
                body = remainder.drop(BULLET_PREFIX.length)
            )
            remainder == BULLET_CHAR.toString() -> ParsedBulletLine(
                indentSpaces = leadingSpaces,
                hasBullet = true,
                body = ""
            )
            else -> ParsedBulletLine(
                indentSpaces = leadingSpaces,
                hasBullet = false,
                body = remainder
            )
        }
    }

    /**
     * Cycles bullet state on the line under [cursorIndex]:
     * - No bullet dot -> adds `• ` (preserving any existing leading spaces)
     * - Top-level bullet dot (`• `) -> adds indentation (`    • `)
     * - Already indented bullet (`    • `) -> removes bullet and indentation back to plain text
     */
    fun toggleOrIndentAtCursor(fullText: String, cursorIndex: Int): TextEditResult {
        val safeCursor = cursorIndex.coerceIn(0, fullText.length)
        val lineStart = findLineStart(fullText, safeCursor)
        val lineEnd = findLineEnd(fullText, safeCursor)
        val line = fullText.substring(lineStart, lineEnd)
        val cursorInLine = (safeCursor - lineStart).coerceAtLeast(0)
        val parsed = parseLine(line)

        val replacement: String
        val nextCursorInLine: Int
        when {
            !parsed.hasBullet -> {
                val indentPrefix = " ".repeat(parsed.indentSpaces)
                replacement = "$indentPrefix$BULLET_PREFIX${parsed.body}"
                nextCursorInLine = if (cursorInLine <= parsed.indentSpaces) {
                    indentPrefix.length + BULLET_PREFIX.length
                } else {
                    cursorInLine + BULLET_PREFIX.length
                }
            }
            parsed.indentSpaces == 0 -> {
                replacement = "$INDENT_UNIT$BULLET_PREFIX${parsed.body}"
                nextCursorInLine = maxOf(
                    INDENT_UNIT.length + BULLET_PREFIX.length,
                    cursorInLine + INDENT_UNIT.length
                )
            }
            else -> {
                replacement = parsed.body
                val removedCount = parsed.indentSpaces + BULLET_PREFIX.length
                nextCursorInLine = (cursorInLine - removedCount).coerceAtLeast(0)
            }
        }

        val updatedText = buildString(fullText.length + 8) {
            append(fullText, 0, lineStart)
            append(replacement)
            append(fullText, lineEnd, fullText.length)
        }
        val clampedCursor = (lineStart + nextCursorInLine.coerceIn(0, replacement.length))
            .coerceIn(0, updatedText.length)
        return TextEditResult(updatedText, clampedCursor)
    }

    /**
     * Handles smart Enter continuation inside a bullet list right after a `\n` was inserted at
     * [newlinePos]. Returns `null` when the previous line was not a bullet item.
     */
    fun handleNewlineAfterBullet(textWithNewline: String, newlinePos: Int): TextEditResult? {
        if (newlinePos < 0 || newlinePos >= textWithNewline.length) return null
        if (textWithNewline[newlinePos] != '\n') return null

        val prevLineStart = findLineStart(textWithNewline, newlinePos)
        val prevLine = textWithNewline.substring(prevLineStart, newlinePos)
        val parsed = parseLine(prevLine)
        if (!parsed.hasBullet) return null

        return if (parsed.body.isNotEmpty()) {
            val continuation = "${" ".repeat(parsed.indentSpaces)}$BULLET_PREFIX"
            val updated = buildString(textWithNewline.length + continuation.length) {
                append(textWithNewline, 0, newlinePos + 1)
                append(continuation)
                append(textWithNewline, newlinePos + 1, textWithNewline.length)
            }
            TextEditResult(updated, newlinePos + 1 + continuation.length)
        } else if (parsed.indentSpaces > 0) {
            val reducedIndent = maxOf(0, parsed.indentSpaces - INDENT_UNIT.length)
            val replacement = "${" ".repeat(reducedIndent)}$BULLET_PREFIX"
            val updated = buildString(textWithNewline.length) {
                append(textWithNewline, 0, prevLineStart)
                append(replacement)
                append(textWithNewline, newlinePos + 1, textWithNewline.length)
            }
            TextEditResult(updated, prevLineStart + replacement.length)
        } else {
            val updated = buildString(textWithNewline.length) {
                append(textWithNewline, 0, prevLineStart)
                append(textWithNewline, newlinePos + 1, textWithNewline.length)
            }
            TextEditResult(updated, prevLineStart)
        }
    }

    private fun findLineStart(text: String, pos: Int): Int {
        if (pos <= 0) return 0
        val prevNewline = text.lastIndexOf('\n', pos - 1)
        return if (prevNewline == -1) 0 else prevNewline + 1
    }

    private fun findLineEnd(text: String, pos: Int): Int {
        val nextNewline = text.indexOf('\n', pos)
        return if (nextNewline == -1) text.length else nextNewline
    }
}
