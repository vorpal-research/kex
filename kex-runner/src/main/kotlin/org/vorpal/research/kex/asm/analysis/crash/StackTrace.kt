package org.vorpal.research.kex.asm.analysis.crash

import org.vorpal.research.kex.util.splitAtLast

data class StackTrace(
    val firstLine: String,
    val stackTraceLines: List<StackTraceElement>
) {
    companion object {
        fun parse(text: String): StackTrace {
            val lines = text.split(System.lineSeparator()).filter { it.isNotBlank() }
            val firstLine = lines.first()
            val stackTraceLines = mutableListOf<StackTraceElement>()
            for (line in lines.drop(1)) {
                val codeElement = line.trim().replace("\\s*at ".toRegex(), "")
                    .substringBefore(' ').trim()
                val (klassAndMethod, location) = codeElement.split('(')
                val (klassName, methodName) = klassAndMethod.splitAtLast('.')
                val (fileName, lineNumber) = when {
                    line.endsWith("(Native Method)") -> null to "-2"
                    line.endsWith("(Unknown Source)") -> null to "-1"
                    else -> location.dropLast(1).splitAtLast(':')
                }
                stackTraceLines += StackTraceElement(klassName, methodName, fileName, lineNumber.toInt())
            }
            return StackTrace(firstLine, stackTraceLines)
        }
    }


    val originalStackTrace: String
        get() = buildString {
            appendLine(firstLine)
            for (line in stackTraceLines)
                appendLine("\tat $line")
        }
}
