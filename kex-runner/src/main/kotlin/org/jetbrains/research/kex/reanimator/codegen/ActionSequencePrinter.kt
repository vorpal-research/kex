package org.jetbrains.research.kex.reanimator.codegen

import org.jetbrains.research.kex.parameters.Parameters
import org.jetbrains.research.kex.reanimator.actionsequence.ActionSequence
import org.jetbrains.research.kfg.ir.Method

interface ActionSequencePrinter {
    val packageName: String
    val klassName: String

    fun printActionSequence(testName: String, method: Method, actionSequences: Parameters<ActionSequence>)

    fun emit(): String
}