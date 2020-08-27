package org.jetbrains.research.kex.generator

import com.abdullin.kthelper.`try`
import com.abdullin.kthelper.logging.debug
import com.abdullin.kthelper.logging.log
import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.generator.callstack.CallStack
import org.jetbrains.research.kex.generator.callstack.CallStackExecutor
import org.jetbrains.research.kex.generator.callstack.CallStackGenerator
import org.jetbrains.research.kex.generator.descriptor.Descriptor
import org.jetbrains.research.kex.generator.descriptor.DescriptorStatistics
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
        var depth = 0
        var successDepths = 0
        repeat(attempts) {
            log.debug("Attempt: $it")
            val kfgClass = cm.randomClass.type
            val klass = ctx.loader.loadClass(kfgClass)

            val any = random.nextOrNull(klass)
            val descriptor = any.descriptor
            val descriptorDepth = descriptor.depth
            depth += descriptorDepth

            log.debug("Depth: $descriptorDepth")
            val originalDescriptor = descriptor.deepCopy()
            val callStack = descriptor.callStack
            val generatedAny = callStack?.let { stack ->
                `try` {
                    CallStackExecutor(ctx).execute(stack)
                }.getOrElse {
                    null
                }
            }

            val structuralEq = originalDescriptor eq generatedAny.descriptor
            if (structuralEq) {
                successDepths += descriptorDepth
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
        log.info("Average random descriptor depth: ${String.format("%.02f", depth.toDouble() / attempts)}")
        log.info("Average success descriptor depth: ${String.format("%.02f", successDepths.toDouble() / successes)}")
    }
}