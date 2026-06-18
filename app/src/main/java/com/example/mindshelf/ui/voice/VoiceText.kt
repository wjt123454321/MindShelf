package com.example.mindshelf.ui.voice

/** Strip common Markdown for TTS playback. */
fun plainTextForSpeech(markdown: String): String {
    if (markdown.isBlank()) return ""
    return markdown
        .replace(Regex("```[\\s\\S]*?```"), " ")
        .replace(Regex("`([^`]+)`"), "$1")
        .replace(Regex("\\[([^\\]]+)]\\([^)]+\\)"), "$1")
        .replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")
        .replace(Regex("^[-*+]\\s+", RegexOption.MULTILINE), "")
        .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
        .replace(Regex("\\*([^*]+)\\*"), "$1")
        .replace(Regex("__([^_]+)__"), "$1")
        .replace(Regex("_([^_]+)_"), "$1")
        .replace(Regex("\\s+"), " ")
        .trim()
}
