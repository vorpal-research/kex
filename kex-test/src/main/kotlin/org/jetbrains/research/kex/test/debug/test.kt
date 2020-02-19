@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug

import java.util.*

class BasicTests {

    enum class Mode { INDENT, PAREN }

    class StackItm(val lineNo: Int,
                   val x: Int,
                   val ch: String,
                   val indentDelta: Int)

    class ParinferOptions(var cursorX: Int?, var cursorLine: Int?, var cursorDx: Int?, var previewCursorScope: Boolean)

    class MutableResult(text: String, val mode: Mode, options: ParinferOptions) {
        val origText: String = text
        val origCursorX: Int? = options.cursorX
        var origLines: List<String> = text.lines()

        var lines: ArrayList<String> = arrayListOf()
        var lineNo: Int = -1
        var ch: String = ""
        var x: Int = 0

        var parenStack: Stack<StackItm> = Stack()

        var parenTrailLineNo: Int? = null
        var parenTrailStartX: Int? = null
        var parenTrailEndX: Int? = null
        var parenTrailOpeners: Stack<StackItm> = Stack()

        var cursorX: Int? = options.cursorX
        var cursorLine: Int? = options.cursorLine
        var cursorDx: Int? = options.cursorDx
        val previewCursorScope: Boolean = options.previewCursorScope
        var canPreviewCursorScope: Boolean = false

        var isInCode: Boolean = true
        var isEscaping: Boolean = false
        var isInStr: Boolean = false
        var isInComment: Boolean = false
        var commentX: Int? = null

        var quoteDanger: Boolean = false
        var trackingIndent: Boolean = false
        var skipChar: Boolean = false
        var success: Boolean = false

        var maxIndent: Int? = null
        var indentDelta: Int = 0

        var error: Exception? = null

//        var errorPosCache: HashMap<Error, ErrorPos> = HashMap()
    }


}