package org.jetbrains.research.kex.reanimator

import org.jetbrains.research.kthelper.`try`
import org.jetbrains.research.kthelper.logging.log
import org.jetbrains.research.kthelper.time.timed
import org.jetbrains.research.kthelper.tryOrNull
import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.asm.util.Visibility
import org.jetbrains.research.kex.asm.util.visibility
import org.jetbrains.research.kex.random.Randomizer
import org.jetbrains.research.kex.reanimator.callstack.CallStack
import org.jetbrains.research.kex.reanimator.callstack.CallStackExecutor
import org.jetbrains.research.kex.reanimator.callstack.generator.CallStackGenerator
import org.jetbrains.research.kex.reanimator.callstack.generator.GeneratorContext
import org.jetbrains.research.kex.reanimator.codegen.TestCasePrinter
import org.jetbrains.research.kex.reanimator.descriptor.*
import org.jetbrains.research.kex.util.kex
import org.jetbrains.research.kex.util.loadClass
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.ir.ConcreteClass
import org.jetbrains.research.kfg.type.ClassType

class RandomObjectReanimator(
    val ctx: ExecutionContext,
    val target: Package,
    val psa: PredicateStateAnalysis,
    val visibilityLevel: Visibility
) {
    val random: Randomizer get() = ctx.random
    val cm: ClassManager get() = ctx.cm
    val generatorContext = GeneratorContext(ctx, psa, visibilityLevel)
    val printer = TestCasePrinter(ctx, target.name, "RandomObjectTests")

    val ClassManager.randomClass
        get() = this.concreteClasses
            .filter { it.`package`.isChild(target) }
            .filterNot { it.isEnum }
            .random()

    val Any.isValid: Boolean
        get() {
            val kfgClass = (this.javaClass.kex.getKfgType(cm.type) as ClassType).`class`
            if (this is Throwable) return false
            if (!kfgClass.isInstantiable || visibilityLevel > kfgClass.visibility) return false
            if (with(generatorContext) { kfgClass.orderedCtors }.isEmpty()) return false
            return true
        }

    fun Descriptor.isValid(visited: Set<Descriptor> = setOf()): Boolean = when (this) {
        in visited -> true
        is ConstantDescriptor -> true
        is ObjectDescriptor -> when {
            this.klass.kfgClass(cm.type) !is ConcreteClass -> false
            this.klass.kfgClass(cm.type).isInheritorOf(cm["java/util/Map"]) -> false
            this.klass.kfgClass(cm.type).isInheritorOf(cm["java/util/Set"]) -> false
            else -> {
                val set = visited + this
                this.fields.all { it.value.isValid(set) }
            }
        }
        is ClassDescriptor -> {
            val set = visited + this
            this.fields.all { it.value.isValid(set) }
        }
        is ArrayDescriptor -> {
            val set = visited + this
            this.elements.all { it.value.isValid(set) }
        }
    }

    private fun randomObject(): Any {
        var res: Any? = null
        while (res == null) {
            val kfgClass = cm.randomClass
            val klass = tryOrNull { ctx.loader.loadClass(kfgClass) } ?: continue
            res = random.nextOrNull(klass)
        }

        return res
    }

    fun run(attempts: Int = 1000) {
        var totalAttempts = 0
        var validAttempts = 0
        var validDescriptorAttempts = 0
        var successes = 0
        var validDescriptorSuccesses = 0
        var depth = 0
        var successDepths = 0
        var totalValidDescriptorDepth = 0
        var validDescriptorDepths = 0
        var time = 0L
        var validTime = 0L
        while (validDescriptorAttempts < attempts) {
            log.debug("Attempt: $totalAttempts")
            log.debug("Valid attempt: $validAttempts")
            log.debug("Valid descriptor attempt: $validDescriptorAttempts")
            ++totalAttempts

            val any = randomObject()
            if (any.isValid) ++validAttempts
            else continue

            val descriptor = any.descriptor
            val descriptorDepth = descriptor.depth
            depth += descriptorDepth

            log.debug("Depth: $descriptorDepth")
            val originalDescriptor = descriptor.deepCopy()

            if (originalDescriptor.isValid()) {
                ++validDescriptorAttempts
                ++validTime
                totalValidDescriptorDepth += descriptorDepth
            }

            var callStack: CallStack? = null
            time += timed {
                callStack = tryOrNull { CallStackGenerator(generatorContext).generateDescriptor(descriptor) }
            }

            val generatedAny = callStack?.let { stack ->
                `try` {
                    CallStackExecutor(ctx).execute(stack)
                }.getOrElse {
                    null
                }
            }

            printer.print(callStack ?: CallStack(""), "test_${totalAttempts}")

            val structuralEq = originalDescriptor eq generatedAny.descriptor
            if (structuralEq) {
                successDepths += descriptorDepth
                ++successes
                if (originalDescriptor.isValid()) {
                    validDescriptorDepths += descriptorDepth
                    ++validDescriptorSuccesses
                }
            }

            log.debug("Equality: $structuralEq")
        }
        printer.emit()

        log.info("Total attempts: $totalAttempts")
        log.info("Valid attempts: $validAttempts")
        log.info("Valid descriptor attempts: $validDescriptorAttempts")
        log.info("Total attempts success rate: ${String.format("%.02f", 100 * successes.toDouble() / totalAttempts)}%")
        log.info("Valid attempts success rate: ${String.format("%.02f", 100 * successes.toDouble() / validAttempts)}%")
        log.info(
            "Valid descriptor attempts success rate: ${
                String.format(
                    "%.02f",
                    100 * validDescriptorSuccesses.toDouble() / validDescriptorAttempts
                )
            }%"
        )
        log.info("Average random descriptor depth: ${String.format("%.02f", depth.toDouble() / validAttempts)}")
        log.info("Average success descriptor depth: ${String.format("%.02f", successDepths.toDouble() / successes)}")
        log.info(
            "Average valid descriptor depth: ${
                String.format(
                    "%.02f",
                    totalValidDescriptorDepth.toDouble() / validDescriptorAttempts
                )
            }"
        )
        log.info(
            "Average success valid descriptor depth: ${
                String.format(
                    "%.02f",
                    validDescriptorDepths.toDouble() / validDescriptorSuccesses
                )
            }"
        )
        log.info("Average time per descriptor generation: ${String.format("%.02f", time.toDouble() / validAttempts)}")
        log.info(
            "Average time per valid descriptor generation: ${
                String.format(
                    "%.02f",
                    validTime.toDouble() / validDescriptorAttempts
                )
            }"
        )
    }
}
