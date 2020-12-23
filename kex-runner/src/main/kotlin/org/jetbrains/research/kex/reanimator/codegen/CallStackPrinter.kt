package org.jetbrains.research.kex.reanimator.codegen

import org.jetbrains.research.kex.reanimator.callstack.CallStack

interface CallStackPrinter {
    val packageName: String
    val klassName: String

    fun printCallStack(callStack: CallStack, method: String)

    fun emit(): String
}