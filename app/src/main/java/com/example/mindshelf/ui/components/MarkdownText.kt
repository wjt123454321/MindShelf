package com.example.mindshelf.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography

/** 修正 AI 输出里表格前缺少空行等常见问题，便于 GFM 解析。 */
internal fun normalizeMarkdownForRender(markdown: String): String {
    if (markdown.isBlank()) return markdown
    val lines = markdown.lines()
    if (lines.isEmpty()) return markdown
    val result = mutableListOf<String>()
    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.startsWith("|") && result.isNotEmpty()) {
            val prev = result.last().trim()
            if (prev.isNotEmpty() && !prev.startsWith("|")) {
                result.add("")
            }
        }
        result.add(line)
    }
    return result.joinToString("\n")
}

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    showCursor: Boolean = false,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    val content = normalizeMarkdownForRender(if (showCursor) "$text▍" else text)
    val colorScheme = MaterialTheme.colorScheme
    @Suppress("DEPRECATION")
    val colors = markdownColor(
        text = colorScheme.onSurface,
        codeText = colorScheme.onSurfaceVariant,
        inlineCodeText = colorScheme.onSurfaceVariant,
        linkText = colorScheme.primary,
        codeBackground = colorScheme.surfaceVariant,
        inlineCodeBackground = colorScheme.surfaceVariant,
        dividerColor = colorScheme.outlineVariant,
        tableBackground = colorScheme.surfaceVariant.copy(alpha = 0.35f),
    )
    val typography = markdownTypography(
        h1 = MaterialTheme.typography.headlineMedium,
        h2 = MaterialTheme.typography.headlineSmall,
        h3 = MaterialTheme.typography.titleLarge,
        h4 = MaterialTheme.typography.titleMedium,
        h5 = MaterialTheme.typography.titleSmall,
        h6 = MaterialTheme.typography.labelLarge,
        text = textStyle,
        paragraph = textStyle,
        ordered = textStyle,
        bullet = textStyle,
        list = textStyle,
        code = MaterialTheme.typography.bodySmall,
        table = textStyle,
    )

    SelectionContainer(modifier = modifier) {
        Markdown(
            content = content,
            colors = colors,
            typography = typography,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
