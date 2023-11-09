package org.vorpal.research.kex

import org.vorpal.research.kex.asm.util.AccessModifier
import org.vorpal.research.kex.random.Randomizer
import org.vorpal.research.kfg.ClassManager
import java.nio.file.Path

data class ExecutionContext(
    val cm: ClassManager,
    val loader: ClassLoader,
    val random: Randomizer,
    val classPath: List<Path>,
    val accessLevel: AccessModifier = AccessModifier.Public
) {
    val types get() = cm.type
    val values get() = cm.value
    val instructions get() = cm.instruction
}
