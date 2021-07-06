package org.jetbrains.research.kex.reanimator

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.asm.util.Visibility
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.descriptor.Descriptor
import org.jetbrains.research.kex.parameters.Parameters
import org.jetbrains.research.kex.parameters.concreteParameters
import org.jetbrains.research.kex.random.GenerationException
import org.jetbrains.research.kex.reanimator.callstack.CallStack
import org.jetbrains.research.kex.reanimator.callstack.generator.UnknownGenerator
import org.jetbrains.research.kex.reanimator.codegen.ExecutorTestCasePrinter
import org.jetbrains.research.kex.reanimator.codegen.klassName
import org.jetbrains.research.kex.reanimator.codegen.packageName
import org.jetbrains.research.kex.smt.SMTModel
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.transformer.generateFinalDescriptors
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kthelper.logging.log
import java.nio.file.Path

private val visibilityLevel by lazy {
    kexConfig.getEnumValue("apiGeneration", "visibility", true, Visibility.PUBLIC)
}

class ExecutionGenerator(val ctx: ExecutionContext, val method: Method) {
    private val csGenerator = UnknownGenerator(ctx, PredicateStateAnalysis(ctx.cm), visibilityLevel)
    private val printer = ExecutorTestCasePrinter(ctx, method.packageName, method.klassName)
    val testKlassName = printer.fullKlassName

    fun generate(descriptors: Parameters<Descriptor>) = try {
        val callStacks = descriptors.callStacks
        printer.print(method, callStacks)
    } catch (e: GenerationException) {
        throw e
    } catch (e: Exception) {
        throw GenerationException(e)
    } catch (e: Error) {
        throw GenerationException(e)
    }

    fun generate(state: PredicateState, model: SMTModel)  {
        val descriptors = generateFinalDescriptors(method, ctx, model, state).concreteParameters(ctx.cm)
        log.debug("Generated descriptors:\n$descriptors")
        generate(descriptors)
    }

    fun emit(): Path {
        printer.emit()
        return printer.targetFile.toPath()
    }

    val Descriptor.callStack: CallStack
        get() = csGenerator.generate(this)

    val Parameters<Descriptor>.callStacks: Parameters<CallStack>
        get() {
            val thisCallStack = instance?.callStack
            val argCallStacks = arguments.map { it.callStack }
            val staticFields = statics.map { it.callStack }.toSet()
            return Parameters(thisCallStack, argCallStacks, staticFields)
        }
}