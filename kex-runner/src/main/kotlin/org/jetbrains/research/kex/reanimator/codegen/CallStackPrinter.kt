package org.jetbrains.research.kex.reanimator.codegen

import org.jetbrains.research.kex.parameters.Parameters
import org.jetbrains.research.kex.reanimator.callstack.CallStack
import org.jetbrains.research.kfg.ir.Method

interface CallStackPrinter {
    val packageName: String
    val klassName: String

    fun printCallStack(testName: String, method: Method, callStacks: Parameters<CallStack>)

    fun emit(): String
}