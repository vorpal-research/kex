package org.vorpal.research.kex.reanimator.codegen

import org.vorpal.research.kex.assertions.ExecutionFinalInfo
import org.vorpal.research.kex.parameters.Parameters
import org.vorpal.research.kex.reanimator.actionsequence.ActionSequence
import org.vorpal.research.kfg.ir.Method

interface ActionSequencePrinter {
    val packageName: String
    val klassName: String

    fun printActionSequence(
        testName: String,
        method: Method,
        actionSequences: Parameters<ActionSequence>,
        finalInfoSequences: ExecutionFinalInfo<ActionSequence>? = null
    )

    fun emit(): String
}