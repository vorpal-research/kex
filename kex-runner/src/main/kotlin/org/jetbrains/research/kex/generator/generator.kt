package org.jetbrains.research.kex.generator

import com.abdullin.kthelper.`try`
import com.abdullin.kthelper.logging.log
import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.generator.callstack.CallStack
import org.jetbrains.research.kex.generator.callstack.CallStackExecutor
import org.jetbrains.research.kex.generator.callstack.CallStackGenerator
import org.jetbrains.research.kex.generator.descriptor.Descriptor
import org.jetbrains.research.kex.generator.descriptor.DescriptorStatistics
import org.jetbrains.research.kex.random.GenerationException
import org.jetbrains.research.kex.smt.SMTModel
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.transformer.generateFinalDescriptors
import org.jetbrains.research.kex.state.transformer.generateInputByModel
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method

class NoConcreteInstanceException(val klass: Class) : Exception()

class Generator(val ctx: ExecutionContext, val psa: PredicateStateAnalysis) {
    val cm: ClassManager get() = ctx.cm
    private val csGenerator = CallStackGenerator(ctx, psa)
    private val csExecutor = CallStackExecutor(ctx)

    fun generateAPI(method: Method, state: PredicateState, model: SMTModel) = try {
        val descriptors = generateFinalDescriptors(method, ctx, model, state).concreteParameters(cm)
        log.debug("Generated descriptors:\n$descriptors")
        val callStacks = descriptors.callStacks
        log.debug("Generated call stacks:\n$callStacks")
        val (instance, arguments, _) = callStacks.executed
        instance to arguments.toTypedArray()
    } catch (e: GenerationException) {
        throw e
    } catch (e: Exception) {
        throw GenerationException(e)
    } catch (e: Error) {
        throw GenerationException(e)
    }

    fun generateFromModel(method: Method, state: PredicateState, model: SMTModel) = try {
        generateInputByModel(ctx, method, state, model)
    } catch (e: GenerationException) {
        throw e
    } catch (e: Exception) {
        throw GenerationException(e)
    }

    private val Descriptor.callStack: CallStack
        get() = `try` {
            val cs = csGenerator.generate(this)
            DescriptorStatistics.addDescriptor(this, cs)
            cs
        }.getOrThrow {
            DescriptorStatistics.addFailure(this)
        }

    private val Parameters<Descriptor>.callStacks: Parameters<CallStack>
        get() {
            val thisCallStack = instance?.callStack
            val argCallStacks = arguments.map { it.callStack }
            val staticFields = staticFields.mapValues { it.value.callStack }
            return Parameters(thisCallStack, argCallStacks, staticFields)
        }

    private val Parameters<CallStack>.executed: Parameters<Any?>
        get() {
            val instance = instance?.let { csExecutor.execute(it) }
            val args = arguments.map { csExecutor.execute(it) }
            val statics = staticFields.mapValues { csExecutor.execute(it.value) }
            return Parameters(instance, args, statics)
        }
}