package org.jetbrains.research.kex.reanimator

import com.abdullin.kthelper.`try`
import com.abdullin.kthelper.logging.log
import com.abdullin.kthelper.time.timed
import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.asm.util.Visibility
import org.jetbrains.research.kex.asm.util.visibility
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.random.Randomizer
import org.jetbrains.research.kex.reanimator.callstack.CallStack
import org.jetbrains.research.kex.reanimator.callstack.CallStackExecutor
import org.jetbrains.research.kex.reanimator.callstack.CallStackGenerator
import org.jetbrains.research.kex.reanimator.callstack.GeneratorContext
import org.jetbrains.research.kex.reanimator.collector.externalConstructors
import org.jetbrains.research.kex.reanimator.descriptor.*
import org.jetbrains.research.kex.util.kex
import org.jetbrains.research.kex.util.loadClass
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.type.ClassType

private val visibilityLevel by lazy { kexConfig.getEnumValue("apiGeneration", "visibility", true, Visibility.PUBLIC) }

class RandomObjectReanimator(val ctx: ExecutionContext, val target: Package, val psa: PredicateStateAnalysis) {
    val random: Randomizer get() = ctx.random
    val cm: ClassManager get() = ctx.cm
    val generatorContext = GeneratorContext(ctx, psa)

    val ClassManager.randomClass
        get() = this.concreteClasses
                .filter { it.`package`.isChild(target) }
                .filterNot { it.isEnum }
                .random()

    fun Descriptor.isValid(visited: Set<Descriptor> = setOf()): Boolean = when (this) {
        in visited -> true
        is ConstantDescriptor.Float -> false
        is ConstantDescriptor.Double -> false
        is ConstantDescriptor -> true
        is ObjectDescriptor -> {
            val set = visited + this
            this.fields.all { it.value.isValid(set) }
        }
        is ArrayDescriptor -> {
            val set = visited + this
            this.elements.all { it.value.isValid(set) }
        }
        is StaticFieldDescriptor -> value.isValid(visited + this)
    }

    val Any.isValid: Boolean
        get() {
            val kfgClass = (this.javaClass.kex.getKfgType(cm.type) as ClassType).`class`
            if (this is Throwable) return false
            if (!kfgClass.isInstantiable || visibilityLevel > kfgClass.visibility) return false
            if (with(generatorContext) { kfgClass.accessibleConstructors + kfgClass.externalConstructors }.isEmpty()) return false
            if (!this.descriptor.isValid()) return false
            return true
        }

    private fun randomObject(): Any {
        var res: Any? = null
        while (res == null) {
            val kfgClass = cm.randomClass
            val klass = ctx.loader.loadClass(kfgClass)
            res = random.nextOrNull(klass)
        }

        return res
    }

    fun run(attempts: Int = 1000) {
        var totalAttempts = 0
        var validAttempts = 0
        var successes = 0
        var depth = 0
        var successDepths = 0
        var time = 0L
        while (validAttempts < attempts) {
            log.debug("Attempt: $totalAttempts")
            log.debug("Valid attempt: $validAttempts")
            ++totalAttempts

            val any = randomObject()
            if (any.isValid) ++validAttempts
            else continue

            val descriptor = any.descriptor
            val descriptorDepth = descriptor.depth
            depth += descriptorDepth

            log.debug("Depth: $descriptorDepth")
            val originalDescriptor = descriptor.deepCopy()

            var callStack: CallStack? = null
            time += timed {
                callStack = `try` { CallStackGenerator(generatorContext).generateDescriptor(descriptor) }.getOrNull()
            }

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

            log.debug("Equality: $structuralEq")
        }
        log.info("Total attempts: $totalAttempts")
        log.info("Valid attempts: $validAttempts")
        log.info("Total attempts success rate: ${String.format("%.02f", 100 * successes.toDouble() / totalAttempts)}%")
        log.info("Valid attempts success rate: ${String.format("%.02f", 100 * successes.toDouble() / validAttempts)}%")
        log.info("Average random descriptor depth: ${String.format("%.02f", depth.toDouble() / validAttempts)}")
        log.info("Average success descriptor depth: ${String.format("%.02f", successDepths.toDouble() / validAttempts)}")
        log.info("Average time per descriptor generation: ${String.format("%.02f", time.toDouble() / validAttempts)}")
    }
}