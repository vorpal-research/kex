package org.jetbrains.research.kex.reanimator.codegen

import org.jetbrains.research.kex.reanimator.callstack.CallStack

interface CallStackPrinter {
    fun print(callStack: CallStack): String
}