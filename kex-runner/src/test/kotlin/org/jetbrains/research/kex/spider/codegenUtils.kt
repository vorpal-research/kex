package org.jetbrains.research.kex.spider

fun StringBuilder.appendLineWithIntent(string: String, n: Int) {
    appendLine(" ".repeat(n) + string)
}

fun String.addIntent(n: Int): String = this.lines().joinToString("\n") { line ->
    if (line.isNotBlank()) " ".repeat(n) + line else ""
}
