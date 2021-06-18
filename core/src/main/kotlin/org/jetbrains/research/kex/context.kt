package org.jetbrains.research.kex

import org.jetbrains.research.kex.random.Randomizer
import org.jetbrains.research.kfg.ClassManager

data class ExecutionContext(
    val cm: ClassManager,
    val loader: ClassLoader,
    val random: Randomizer
) {
    val types get() = cm.type
    val values get() = cm.value
    val instructions get() = cm.instruction
}