package ai.devcodex.agents.ui.markdown

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class Code(val language: String?, val code: String) : MarkdownBlock
    data class ListItem(val ordered: Boolean, val text: String) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data object Divider : MarkdownBlock
}

object MarkdownParser {

    private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
    private val BULLET = Regex("^[-*+]\\s+(.*)$")
    private val NUMBERED = Regex("^\\d{1,3}[\\.\\)]\\s+(.*)$")
    private val QUOTE = Regex("^>\\s?(.*)$")

    fun parse(markdown: String): List<MarkdownBlock> {
        val lines = markdown.lines()
        val blocks = mutableListOf<MarkdownBlock>()
        var i = 0
        val n = lines.size

        fun addParagraph(text: String) {
            if (text.isNotBlank()) blocks.add(MarkdownBlock.Paragraph(text.trim()))
        }

        fun flushBuffer(buffer: MutableList<String>) {
            if (buffer.isNotEmpty()) addParagraph(buffer.joinToString(" "))
        }

        val buffer = mutableListOf<String>()
        while (i < n) {
            val line = lines[i]
            if (line.isBlank()) {
                flushBuffer(buffer); buffer.clear(); i++; continue
            }

            if (line.trimStart().startsWith("```")) {
                flushBuffer(buffer); buffer.clear()
                val lang = line.trimStart().removePrefix("```").trim().ifBlank { null }
                val codeLines = mutableListOf<String>()
                i++
                while (i < n && !lines[i].trimStart().startsWith("```")) {
                    codeLines.add(lines[i]); i++
                }
                i++
                blocks.add(MarkdownBlock.Code(lang, codeLines.joinToString("\n")))
                continue
            }

            val trimmed = line.trimStart()
            val heading = HEADING.find(trimmed)
            if (heading != null) {
                flushBuffer(buffer); buffer.clear()
                blocks.add(MarkdownBlock.Heading(heading.groupValues[1].length, heading.groupValues[2]))
                i++; continue
            }

            if (trimmed.matches(Regex("^-{3,}$")) || trimmed.matches(Regex("^\\*{3,}$"))) {
                flushBuffer(buffer); buffer.clear()
                blocks.add(MarkdownBlock.Divider)
                i++; continue
            }

            val bullet = BULLET.find(trimmed)
            if (bullet != null) {
                flushBuffer(buffer); buffer.clear()
                blocks.add(MarkdownBlock.ListItem(false, bullet.groupValues[1]))
                i++; continue
            }

            val numbered = NUMBERED.find(trimmed)
            if (numbered != null) {
                flushBuffer(buffer); buffer.clear()
                blocks.add(MarkdownBlock.ListItem(true, numbered.groupValues[1]))
                i++; continue
            }

            val quote = QUOTE.find(trimmed)
            if (quote != null) {
                flushBuffer(buffer); buffer.clear()
                blocks.add(MarkdownBlock.Quote(quote.groupValues[1]))
                i++; continue
            }

            buffer.add(line)
            i++
        }
        flushBuffer(buffer)
        return blocks
    }

    private val INLINE_PATTERNS = listOf(
        Regex("^`[^`]+`"),
        Regex("^\\*\\*(.+?)\\*\\*"),
        Regex("^__(.+?)__"),
        Regex("^\\*(.+?)\\*"),
        Regex("^_(.+?)_"),
        Regex("^\\[([^\\]]+)\\]\\(([^)\\s]+)\\)")
    )

    fun renderInline(text: String, linkColor: Color): AnnotatedString {
        return buildAnnotatedString {
            var i = 0
            val n = text.length
            while (i < n) {
                val rest = text.substring(i)
                val match = INLINE_PATTERNS.firstNotNullOfOrNull { p -> p.find(rest) }
                if (match == null) {
                    append(rest[0])
                    i++
                    continue
                }
                val whole = match.value
                when {
                    whole.startsWith("`") -> {
                        pushStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = Color(0x1A000000),
                                color = linkColor
                            )
                        )
                        append(whole.substring(1, whole.length - 1))
                        pop()
                    }
                    whole.startsWith("**") || whole.startsWith("__") -> {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        append(whole.substring(2, whole.length - 2))
                        pop()
                    }
                    whole.startsWith("*") || whole.startsWith("_") -> {
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        append(whole.substring(1, whole.length - 1))
                        pop()
                    }
                    whole.startsWith("[") -> {
                        val label = match.groups[1]?.value ?: ""
                        pushStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
                        append(label)
                        pop()
                    }
                    else -> append(whole)
                }
                i += whole.length
            }
        }
    }
}

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val quoteColor = MaterialTheme.colorScheme.surfaceVariant
    val headingColor = MaterialTheme.colorScheme.onSurface
    val textColor = MaterialTheme.colorScheme.onSurface

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        var orderedIndex = 0
        MarkdownParser.parse(text).forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    val s = when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium
                    }
                    Text(
                        text = MarkdownParser.renderInline(block.text, linkColor),
                        style = s.copy(color = headingColor, fontWeight = FontWeight.Bold)
                    )
                }
                is MarkdownBlock.Paragraph -> SelectionContainer {
                    Text(
                        text = MarkdownParser.renderInline(block.text, linkColor),
                        style = style.copy(color = textColor)
                    )
                }
                is MarkdownBlock.Code -> CodeBlockView(block)
                is MarkdownBlock.ListItem -> {
                    val prefix = if (block.ordered) {
                        orderedIndex++; "$orderedIndex. "
                    } else {
                        orderedIndex = 0; "• "
                    }
                    SelectionContainer {
                        Text(
                            text = buildAnnotatedString {
                                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = linkColor))
                                append(prefix)
                                pop()
                                append(MarkdownParser.renderInline(block.text, linkColor))
                            },
                            style = style.copy(color = textColor)
                        )
                    }
                }
                is MarkdownBlock.Quote -> Text(
                    text = MarkdownParser.renderInline(block.text, linkColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(quoteColor, RoundedCornerShape(6.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    style = style.copy(color = textColor)
                )
                MarkdownBlock.Divider -> HorizontalDivider()
            }
        }
    }
}

@Composable
fun CodeBlockView(block: MarkdownBlock.Code) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0D1117), RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = block.language ?: "code",
                color = Color(0xFF8B949E),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            IconButton(
                onClick = {
                    scope.launch {
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("code", block.code)))
                    }
                }
            ) {
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = "复制代码",
                    tint = Color(0xFF8B949E),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            SelectionContainer {
                Text(
                    text = CodeHighlighter.highlight(block.code, block.language),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}