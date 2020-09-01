package org.jetbrains.research.kex.generator

import com.abdullin.kthelper.`try`
import com.abdullin.kthelper.logging.debug
import com.abdullin.kthelper.logging.log
import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.asm.util.Visibility
import org.jetbrains.research.kex.asm.util.visibility
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.generator.callstack.CallStackExecutor
import org.jetbrains.research.kex.generator.callstack.CallStackGenerator
import org.jetbrains.research.kex.generator.descriptor.descriptor
import org.jetbrains.research.kex.generator.descriptor.isInstantiable
import org.jetbrains.research.kex.random.Randomizer
import org.jetbrains.research.kex.util.loadClass
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.Package

private val visibilityLevel by lazy { kexConfig.getEnumValue("apiGeneration", "visibility", true, Visibility.PUBLIC) }

class RandomDescriptorGenerator(val ctx: ExecutionContext, val target: Package, val psa: PredicateStateAnalysis) {
    val random: Randomizer get() = ctx.random
    val cm: ClassManager get() = ctx.cm

    val ClassManager.randomClass
        get() = this.concreteClasses
                .filter { it.`package`.isChild(target) }
                .filterNot { it.isEnum }
                .random()



    private fun randomObject(): Any {
        var res: Any? = null
        while (res == null) {
            val kfgClass = cm.randomClass
            if (!kfgClass.isInstantiable || visibilityLevel > kfgClass.visibility) continue

            val klass = ctx.loader.loadClass(kfgClass)
            res = random.nextOrNull(klass)
        }

        return res
    }

    fun run(attempts: Int = 1000) {
        var successes = 0
        var depth = 0
        var successDepths = 0
        repeat(attempts) {
            log.debug("Attempt: $it")

            val any = randomObject()
            val descriptor = any.descriptor
            val descriptorDepth = descriptor.depth
            depth += descriptorDepth

            log.debug("Depth: $descriptorDepth")
            val originalDescriptor = descriptor.deepCopy()
            val callStack = `try` { CallStackGenerator(ctx, psa).generate(descriptor) }.getOrNull()
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