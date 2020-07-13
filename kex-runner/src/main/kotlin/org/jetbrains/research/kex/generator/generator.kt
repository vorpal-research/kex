package org.jetbrains.research.kex.generator

import com.abdullin.kthelper.`try`
import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.annotations.AnnotationManager
import org.jetbrains.research.kex.asm.analysis.KexCheckerException
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.generator.descriptor.Descriptor
import org.jetbrains.research.kex.random.GenerationException
import org.jetbrains.research.kex.smt.Checker
import org.jetbrains.research.kex.smt.Result
import org.jetbrains.research.kex.smt.SMTModel
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.emptyState
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.term
import org.jetbrains.research.kex.state.transformer.*
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method

private val apiGeneration get() = kexConfig.getBooleanValue("apiGeneration", "enabled", false)
private val useConcreteImpl get() = kexConfig.getBooleanValue("apiGeneration", "use-concrete-impl", false)

class NoConcreteInstanceException(val klass: Class) : Exception()

class Generator(val ctx: ExecutionContext, val psa: PredicateStateAnalysis) {
    val cm: ClassManager get() = ctx.cm
    private val csGenerator = CallStackGenerator(ctx, psa)
    private val csExecutor = CallStackExecutor(ctx)
    private val descriptorLog = log("descriptors")

    private fun generateAPI(method: Method, state: PredicateState, model: SMTModel) = try {
        val descriptors = generateFinalDescriptors(method, ctx, model, state).concrete
        descriptorLog.debug("Generated descriptors:\n$descriptors")
        val callStacks = descriptors.callStacks
        descriptorLog.debug("Generated call stacks:\n$callStacks")
        val (instance, arguments, _) = callStacks.executed
        instance to arguments.toTypedArray()
    } catch (e: GenerationException) {
        throw e
    } catch (e: Exception) {
        throw GenerationException(e)
    } catch (e: Error) {
        throw GenerationException(e)
    }

    private fun generateFromModel(method: Method, state: PredicateState, model: SMTModel) = try {
        generateInputByModel(ctx, method, state, model)
    } catch (e: GenerationException) {
        throw e
    } catch (e: Exception) {
        throw GenerationException(e)
    }

    private val Parameters<Descriptor>.concrete
        get() = Parameters(instance?.concretize(cm), arguments.map { it.concretize(cm) }, staticFields.mapValues { it.value.concretize(cm) })

    private val Descriptor.callStack: CallStack
        get() = `try` {
            val cs = csGenerator.generate(this)
            DescriptorStatistics.addDescriptor(this, cs)
            cs
        }.getOrThrow {
            DescriptorStatistics.addFailure(this)
        }

    private val Parameters<Descriptor>.callStacks: Parameters<CallStack> get() {
        val thisCallStack = instance?.callStack
        val argCallStacks = arguments.map { it.callStack }
        val staticFields = staticFields.mapValues { it.value.callStack }
        return Parameters(thisCallStack, argCallStacks, staticFields)
    }

    private val Parameters<CallStack>.executed: Parameters<Any?> get() {
        val instance = instance?.let { csExecutor.execute(it) }
        val args = arguments.map { csExecutor.execute(it) }
        val statics = staticFields.mapValues { csExecutor.execute(it.value) }
        return Parameters(instance, args, statics)
    }

    private val Parameters<Descriptor>.typeInfoState: PredicateState
        get() {
            val thisState = instance?.run {
                TermRemapper(mapOf(term to term { `this`(term.type) })).apply(typeInfo)
            }
            val argStates = arguments.mapIndexed { index, descriptor ->
                val typeInfo = descriptor.typeInfo
                TermRemapper(mapOf(descriptor.term to term { arg(descriptor.term.type, index) })).apply(typeInfo)
            }.toTypedArray()
            return listOfNotNull(thisState, *argStates).fold(emptyState()) { acc, predicateState -> acc + predicateState }
        }

    private fun prepareState(method: Method, ps: PredicateState, typeInfoMap: TypeInfoMap) = transform(ps) {
        +AnnotationIncluder(method, AnnotationManager.defaultLoader)
        +DepthInliner(ctx, typeInfoMap, psa)
        +IntrinsicAdapter
        +ReflectionInfoAdapter(method, ctx.loader)
        +Optimizer()
        +ConstantPropagator
        +BoolTypeAdapter(method.cm.type)
        +ArrayBoundsAdapter()
        +NullityInfoAdapter()
    }

    private fun reExecute(method: Method, block: BasicBlock, typeInfoState: PredicateState): Pair<Any?, Array<Any?>> {
        val checker = Checker(method, ctx.loader, psa)
        val ps = typeInfoState.let {
            val ps = checker.createState(block.terminator)!!
            val (t, a) = collectArguments(it)
            val (at, aa) = collectArguments(ps)
            val map = mutableMapOf<Term, Term>()
            at?.run { map[at] == t }
            aa.forEach { (key, value) -> a[key]?.let { map[value] = it } }
            TermRemapper(map).apply(ps)
        }
        val typeInfoMap = collectPlainTypeInfos(ctx.types, typeInfoState)
        val preparedState = prepareState(method, ps, typeInfoMap)

        val result = try {
            checker.check(preparedState, preparedState.path)
        } catch (e: Exception) {
            throw KexCheckerException(e, ps)
        }
        return when (result) {
            is Result.SatResult -> generateAPI(method, checker.state, result.model)
            else -> throw GenerationException("ReExecuted state can't be solved: $result")
        }
    }

    private fun generateConcreteAPI(method: Method, block: BasicBlock, state: PredicateState, model: SMTModel) = try {
        val descriptors = generateFinalDescriptors(method, ctx, model, state).concrete
        val typeInfoState = descriptors.typeInfoState
        reExecute(method, block, typeInfoState)
    } catch (e: GenerationException) {
        throw e
    } catch (e: NoClassDefFoundError) {
        throw GenerationException(e)
    } catch (e: Exception) {
        throw GenerationException(e)
    }

    fun generate(method: Method, block: BasicBlock, state: PredicateState, model: SMTModel) = when {
        apiGeneration -> when {
            useConcreteImpl -> generateConcreteAPI(method, block, state, model)
            else -> generateAPI(method, state, model)
        }
        else -> generateFromModel(method, state, model)
    }
}