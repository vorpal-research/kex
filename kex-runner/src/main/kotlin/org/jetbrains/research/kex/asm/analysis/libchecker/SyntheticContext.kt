package org.jetbrains.research.kex.asm.analysis.libchecker

import org.jetbrains.research.kfg.ir.Field
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.Argument
import org.jetbrains.research.libsl.asg.State
import org.jetbrains.research.libsl.asg.Variable

class SyntheticContext {
    val fields = mutableMapOf<Variable, Field>()
    val methodsArgs = mutableMapOf<Method, List<Argument>>()
    val statesMap = mutableMapOf<State, Int>()
    lateinit var stateField: Field
}