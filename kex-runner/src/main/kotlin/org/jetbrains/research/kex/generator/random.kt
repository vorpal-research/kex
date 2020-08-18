package org.jetbrains.research.kex.generator

import com.abdullin.kthelper.`try`
import com.abdullin.kthelper.logging.debug
import com.abdullin.kthelper.logging.log
import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.generator.descriptor.Descriptor
import org.jetbrains.research.kex.generator.descriptor.descriptor
import org.jetbrains.research.kex.ktype.type
import org.jetbrains.research.kex.random.Randomizer
import org.jetbrains.research.kex.util.loadClass
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.Package

class RandomDescriptorGenerator(val ctx: ExecutionContext, val target: Package, val psa: PredicateStateAnalysis) {
    val random: Randomizer get() = ctx.random
    val cm: ClassManager get() = ctx.cm

    val ClassManager.randomClass get() = this.concreteClasses
            .filter { it.`package`.isChild(target) }
            .filterNot { it.isEnum }
            .random()

    val Descriptor.callStack: CallStack?
        get() = `try`<CallStack?> {
            val cs = CallStackGenerator(ctx, psa).generate(this)
            DescriptorStatistics.addDescriptor(this, cs)
            cs
        }.getOrElse {
            DescriptorStatistics.addFailure(this)
            null
        }

    fun run(attempts: Int = 1000) {
        var successes = 0
        repeat(attempts) { it ->
            log.debug("Attempt: $it")
            val kfgClass = cm.randomClass.type
            val klass = ctx.loader.loadClass(kfgClass)

            val any = random.nextOrNull(klass)
            val descriptor = any.descriptor
            val callStack = descriptor.callStack ?: return@repeat
            val generatedAny = `try` { CallStackExecutor(ctx).execute(callStack) }.getOrElse {
                null
            }

            val structuralEq = descriptor eq generatedAny.descriptor
            if (structuralEq) {
                ++successes
            }

            log.run {
//                debug("Original object: $any")
//                debug("Descriptor: $descriptor")
//                debug("Call stack: ${callStack.print()}")
//                debug("Generated object: $generatedAny")
                debug("Equality: $structuralEq")
                debug()
            }
        }
        log.info("Random descriptor generation success rate: ${String.format("%.02f", 100 * successes.toDouble() / attempts)}%")
    }
}