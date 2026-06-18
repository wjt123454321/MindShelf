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

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    showCursor: Boolean = false,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    val content = if (showCursor) "$text▍" else text
    val colorScheme = MaterialTheme.colorScheme
    val colors = markdownColor(
        text = colorScheme.onSurface,
        codeText = colorScheme.onSurfaceVariant,
        inlineCodeText = colorScheme.onSurfaceVariant,
        linkText = colorScheme.primary,
        codeBackground = colorScheme.surfaceVariant,
        inlineCodeBackground = colorScheme.surfaceVariant,
        dividerColor = colorScheme.outlineVariant,
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
