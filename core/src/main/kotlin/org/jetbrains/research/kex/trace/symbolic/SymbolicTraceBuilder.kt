@file:Suppress("unused", "DuplicatedCode")

package org.jetbrains.research.kex.trace.symbolic

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.descriptor.*
import org.jetbrains.research.kex.ktype.*
import org.jetbrains.research.kex.parameters.Parameters
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.StateBuilder
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.predicate.path
import org.jetbrains.research.kex.state.predicate.state
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.term
import org.jetbrains.research.kex.state.transformer.TermRenamer
import org.jetbrains.research.kex.util.cmp
import org.jetbrains.research.kex.util.parseValue
import org.jetbrains.research.kfg.ir.*
import org.jetbrains.research.kfg.ir.value.*
import org.jetbrains.research.kfg.ir.value.instruction.*
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.type.parseDesc
import org.jetbrains.research.kthelper.KtException
import org.jetbrains.research.kthelper.`try`
import org.jetbrains.research.kthelper.assert.ktassert
import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.collection.stackOf
import org.jetbrains.research.kthelper.logging.log

class SymbolicTraceException(message: String, cause: Throwable) : KtException(message, cause) {
    override fun toString() = "SymbolicTraceException: $message: ${cause?.message}"
}

/**
 * Class that collects the symbolic state of the program during the execution
 */
class SymbolicTraceBuilder(
    val ctx: ExecutionContext,
    val nameMapperContext: NameMapperContext
) : SymbolicState(), InstructionTraceCollector {
    /**
     * required fields
     */
    override val state: PredicateState
        get() = builder.current
    override val path: PathCondition
        get() = PathConditionImpl(pathBuilder.toList())
    override val concreteValueMap: Map<Term, Descriptor>
        get() = concreteValues.toMap()
    override val termMap: Map<Term, WrappedValue>
        get() = terms.toMap()
    override val predicateMap: Map<Predicate, Instruction>
        get() = predicates.toMap()

    override val symbolicState: SymbolicState
        get() = SymbolicStateImpl(
            state,
            path,
            concreteValueMap,
            termMap,
            predicateMap,
            trace
        )
    override val trace: InstructionTrace
        get() = InstructionTrace(traceBuilder.toList())

    /**
     * mutable backing fields for required fields
     */
    private val cm get() = ctx.cm
    private val converter = Object2DescriptorConverter()
    private val builder = StateBuilder()
    private val traceBuilder = arrayListOf<Instruction>()
    private val pathBuilder = arrayListOf<Clause>()
    private val concreteValues = mutableMapOf<Term, Descriptor>()
    private val terms = mutableMapOf<Term, WrappedValue>()
    private val predicates = mutableMapOf<Predicate, Instruction>()

    /**
     * stack frame info for method
     */
    private val frames = FrameStack()

    private val nameGenerator = NameGenerator()
    private val currentFrame get() = frames.peek()
    private val currentMethod get() = currentFrame.method
    private val valueMap get() = currentFrame.valueMap

    /**
     * try-catch info
     */
    private val catchHandlers = stackOf<MutableMap<Class, Frame>>()

    /**
     * necessary runtime info
     */
    private var lastCall: Call? = null
    private var thrownException: Term? = null
    private var previousBlock: BasicBlock
        get() = currentFrame.previousBlock
        set(block) {
            currentFrame.previousBlock = block
        }

    private class FrameStack : Iterable<Frame> {
        private val frames = stackOf<Frame>()

        fun push(element: Frame) = frames.push(element)
        fun pop(): Frame = frames.pop()
        fun peek(): Frame = frames.peek()

        override fun iterator() = frames.iterator()

        fun isEmpty() = frames.isEmpty()
        fun isNotEmpty() = frames.isNotEmpty()
    }

    private data class Frame(
        val method: Method,
        val valueMap: MutableMap<Value, Term>,
        val returnReceiver: Pair<Value, Term>?
    ) {
        val catchMap = mutableMapOf<Type, Map<Value, Term>>()
        var previousBlock = method.entry

    }

    private class NameGenerator {
        private val names = mutableMapOf<String, Int>()
        fun nextName(name: String): String {
            val index = names.getOrPut(name) { 0 }
            names[name] = index + 1
            return "${name}_$index"
        }
    }

    private data class Call(
        val call: CallInst,
        val method: Method,
        val receiver: Pair<Value, Term>?,
        val params: Parameters<Term>,
        val predicate: Predicate
    )

    override fun toString() = "$state"

    private fun String.toType() = parseDesc(cm.type, this)

    private fun safeCall(body: () -> Unit) = `try` {
        body()
    }.getOrThrow {
        SymbolicTraceException("", this)
    }

    private fun parseMethod(className: String, methodName: String, args: List<String>, retType: String): Method {
        val klass = cm[className]
        return klass.getMethod(methodName, MethodDesc(args.map { it.toType() }.toTypedArray(), retType.toType()))
    }

    private fun parseBlock(blockName: String): BasicBlock {
        val nm = nameMapperContext.getMapper(currentMethod)
        return nm.getBlock(blockName) ?: unreachable {
            log.error("Unknown block name $blockName")
        }
    }

    private fun parseValue(valueName: String): Value {
        val nm = nameMapperContext.getMapper(currentMethod)
        return nm.parseValue(valueName)
    }

    private fun newValue(value: Value) = term {
        when (value) {
            is Argument -> arg(value)
            is Constant -> const(value)
            is ThisRef -> `this`(value.type.kexType)
            else -> tf.getValue(value.type.kexType, nameGenerator.nextName("${value.name}"))
        }
    }

    private fun mkValue(value: Value) = valueMap.getOrPut(value) { newValue(value) }

    private fun mkNewValue(value: Value): Term {
        val v = newValue(value)
        valueMap[value] = v
        return v
    }

    private fun Descriptor.unwrapped(type: KexType) = when (type) {
        is KexIntegral, is KexReal -> when (this) {
            is ObjectDescriptor -> when {
                type is KexBool && this.type == KexClass("java/lang/Integer") -> {
                    val value = this["value", KexInt()]!! as ConstantDescriptor.Int
                    if (value.value == 0) descriptor { const(false) }
                    else descriptor { const(true) }
                }
                type is KexInt && this.type == KexClass("java/lang/Boolean") -> {
                    val value = this["value", KexBool()]!! as ConstantDescriptor.Bool
                    if (value.value) descriptor { const(1) }
                    else descriptor { const(0) }
                }
                type is KexInt && this.type == KexClass("java/lang/Character") -> {
                    val value = this["value", KexChar()]!! as ConstantDescriptor.Char
                    descriptor { const(value.value.code) }
                }
                else -> this["value", type]!!
            }
            else -> this
        }
        else -> this
    }

    private fun Value.wrapped(): WrappedValue = WrappedValue(currentMethod, this)

    private fun Term.updateInfo(value: Value, concreteValue: Descriptor) {
        terms.getOrPut(this) { value.wrapped() }
        concreteValues.getOrPut(this) { concreteValue }
    }

    private fun Any?.getAsDescriptor() = converter.convert(this)
    private fun Any?.getAsDescriptor(type: KexType) = converter.convert(this).unwrapped(type)

    private val Method.parameterValues: Parameters<Value>
        get() = Parameters(
            if (!isStatic) ctx.values.getThis(klass) else null,
            argTypes.withIndex().map { (index, type) -> ctx.values.getArgument(index, this, type) },
            setOf()
        )

    private infix fun Method.overrides(other: Method): Boolean = when {
        this == other -> true
        other.isFinal -> false
        this.klass !is ConcreteClass -> false
        other.klass !is ConcreteClass -> false
        this.name != other.name -> false
        this.desc != other.desc -> false
        !this.klass.isInheritorOf(other.klass) -> false
        else -> true
    }

    override fun methodEnter(
        className: String,
        methodName: String,
        argTypes: List<String>,
        retType: String,
        instance: Any?,
        args: List<Any?>
    ) = safeCall {
        val method = parseMethod(className, methodName, argTypes, retType)
        frames.push(Frame(method, mutableMapOf(), lastCall?.receiver))
        if (lastCall != null) {
            val call = lastCall!!
            ktassert(call.method overrides method)

            for ((value, term) in method.parameterValues.asList.zip(call.params.asList)) {
                valueMap[value] = term
            }
            lastCall = null
        }
    }

    private fun checkCall() = safeCall {
        if (lastCall != null) {
            builder += lastCall!!.predicate
            predicates[lastCall!!.predicate] = lastCall!!.call
            lastCall = null
        }
    }

    private fun updateCatches(instruction: Instruction) {
        if (frames.isNotEmpty()) {
            val block = instruction.parent
            val frame = currentFrame
            val updatesValues = valueMap.toMap()
            frame.catchMap.clear()
            for (handler in block.handlers) {
                val exception = handler.exception
                frame.catchMap[exception] = updatesValues
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    /**
     * used to prepare StateBuilder to instruction processing
     */
    private fun preProcess(instruction: Instruction) {
        checkCall()
    }

    /**
     * used to save all the necessary information after instruction processing
     *
     * used everywhere except: call, jump, return
     * for more information @see(SymbolicTraceBuilder.call), @see(SymbolicTraceBuilder.jump), @see(SymbolicTraceBuilder.ret)
     */
    private fun postProcess(instruction: Instruction, predicate: Predicate) {
        predicates[predicate] = instruction
        builder += predicate

        traceBuilder += instruction
        updateCatches(instruction)
    }

    private fun processPath(instruction: Instruction, predicate: Predicate) {
        previousBlock = instruction.parent
        pathBuilder += Clause(instruction, predicate)
    }

    private fun restoreCatchFrame(exceptionType: Type) {
        do {
            val frame = currentFrame
            val candidates = frame.catchMap.keys.filter { exceptionType.isSubtypeOf(it) }
            val candidate = candidates.find { candidate -> candidates.all { candidate.isSubtypeOf(it) } }
            if (candidate != null) {
                valueMap.clear()
                valueMap.putAll(frame.catchMap[candidate]!!)
                return
            }
            frames.pop()
        } while (frames.isNotEmpty())
        unreachable<Unit> { log.error("Could not find a catch block for exception type $exceptionType") }
    }

    /**
     * instruction handling methods
     */
    override fun arrayLoad(
        value: String,
        arrayRef: String,
        index: String,
        concreteValue: Any?,
        concreteRef: Any?,
        concreteIndex: Any?
    ) = safeCall {
        val kfgValue = parseValue(value) as ArrayLoadInst
        preProcess(kfgValue)

        val kfgRef = parseValue(arrayRef)
        val kfgIndex = parseValue(index)

        val termValue = mkNewValue(kfgValue)
        val termRef = mkValue(kfgRef)
        val termIndex = mkValue(kfgIndex)

        terms[termValue] = kfgValue.wrapped()
        concreteValues[termValue] = concreteValue.getAsDescriptor(termValue.type)

        termRef.updateInfo(kfgRef, concreteRef.getAsDescriptor(termRef.type))
        termIndex.updateInfo(kfgIndex, concreteIndex.getAsDescriptor(termIndex.type))

        val predicate = state(kfgValue.location) {
            termValue equality termRef[termIndex].load()
        }

        postProcess(kfgValue, predicate)
    }

    override fun arrayStore(
        inst: String,
        arrayRef: String,
        index: String,
        value: String,
        concreteRef: Any?,
        concreteIndex: Any?,
        concreteValue: Any?
    ) = safeCall {
        val instruction = parseValue(inst) as ArrayStoreInst
        preProcess(instruction)

        val kfgRef = parseValue(arrayRef)
        val kfgIndex = parseValue(index)
        val kfgValue = parseValue(value)

        val termRef = mkValue(kfgRef)
        val termIndex = mkValue(kfgIndex)
        val termValue = mkValue(kfgValue)

        termRef.updateInfo(kfgRef, concreteRef.getAsDescriptor(termRef.type))
        termIndex.updateInfo(kfgIndex, concreteIndex.getAsDescriptor(termIndex.type))
        termValue.updateInfo(kfgValue, concreteValue.getAsDescriptor(termValue.type))

        val predicate = state(instruction.location) {
            termRef[termIndex].store(termValue)
        }

        postProcess(instruction, predicate)
    }

    override fun binary(
        value: String,
        lhv: String,
        rhv: String,
        concreteValue: Any?,
        concreteLhv: Any?,
        concreteRhv: Any?
    ) = safeCall {
        val kfgValue = parseValue(value) as BinaryInst
        preProcess(kfgValue)

        val kfgLhv = parseValue(lhv)
        val kfgRhv = parseValue(rhv)

        val termValue = mkNewValue(kfgValue)
        val termLhv = mkValue(kfgLhv)
        val termRhv = mkValue(kfgRhv)

        terms[termValue] = kfgValue.wrapped()
        concreteValues[termValue] = concreteValue.getAsDescriptor(termValue.type)

        termLhv.updateInfo(kfgLhv, concreteLhv.getAsDescriptor(termLhv.type))
        termRhv.updateInfo(kfgRhv, concreteRhv.getAsDescriptor(termRhv.type))

        val predicate = state(kfgValue.location) {
            termValue equality termLhv.apply(cm.type, kfgValue.opcode, termRhv)
        }

        postProcess(kfgValue, predicate)
    }

    override fun branch(
        inst: String,
        condition: String
    ) = safeCall {
        val instruction = parseValue(inst) as BranchInst
        preProcess(instruction)

        val kfgCondition = parseValue(condition)
        val termCondition = mkValue(kfgCondition)
        val booleanValue = (concreteValues[termCondition] as? ConstantDescriptor.Bool)?.value
            ?: unreachable { log.error("Unknown boolean value in branch") }

        val predicate = path(instruction.location) {
            termCondition equality booleanValue
        }

        processPath(instruction, predicate)
        postProcess(instruction, predicate)
    }

    override fun call(
        inst: String,
        className: String,
        methodName: String,
        argTypes: List<String>,
        retType: String,
        returnValue: String?,
        callee: String?,
        arguments: List<String>,
        concreteArguments: List<Any?>
    ) = safeCall {
        val instruction = parseValue(inst) as CallInst
        preProcess(instruction)

        val calledMethod = parseMethod(className, methodName, argTypes, retType)
        val kfgReturn = returnValue?.let { parseValue(it) }
        val kfgCallee = callee?.let { parseValue(it) }
        val kfgArguments = arguments.map { parseValue(it) }

        val termReturn = kfgReturn?.let { mkNewValue(it) }
        val termCallee = kfgCallee?.let { mkValue(it) }
        val termArguments = kfgArguments.map { mkValue(it) }

        termReturn?.apply { terms[this] = kfgReturn.wrapped() }

        termCallee?.apply {
            terms.getOrPut(this) { kfgCallee.wrapped() }
        }
        termArguments.withIndex().forEach { (index, term) ->
            term.updateInfo(kfgArguments[index], concreteArguments[index].getAsDescriptor(term.type))
        }

        val predicate = state(instruction.location) {
            val actualCallee = termCallee ?: `class`(calledMethod.klass)
            when {
                termReturn != null -> termReturn equality actualCallee.call(calledMethod, termArguments)
                else -> call(actualCallee.call(calledMethod, termArguments))
            }
        }

        lastCall = Call(
            instruction,
            calledMethod,
            termReturn?.let { kfgReturn to it },
            Parameters(termCallee, termArguments, setOf()),
            predicate
        )

        /**
         * we do not use 'postProcess' here, because actual call predicate may not end up in the final state
         */
        traceBuilder += instruction
        updateCatches(instruction)
    }

    override fun cast(
        value: String,
        operand: String,
        concreteValue: Any?,
        concreteOperand: Any?
    ) = safeCall {
        val kfgValue = parseValue(value) as CastInst
        preProcess(kfgValue)

        val kfgOperand = parseValue(operand)
        val termValue = mkNewValue(kfgValue)
        val termOperand = mkValue(kfgOperand)

        terms[termValue] = kfgValue.wrapped()
        concreteValues[termValue] = concreteValue.getAsDescriptor(termValue.type)

        termOperand.updateInfo(kfgOperand, concreteOperand.getAsDescriptor(termOperand.type))

        val predicate = state(kfgValue.location) {
            termValue equality (termOperand `as` kfgValue.type.kexType)
        }

        postProcess(kfgValue, predicate)
    }

    override fun catch(
        exception: String,
        concreteException: Any?
    ) = safeCall {
        val exceptionDescriptor = converter.convert(concreteException)
        restoreCatchFrame(exceptionDescriptor.type.getKfgType(ctx.types))

        val kfgException = parseValue(exception) as CatchInst
        preProcess(kfgException)

        val termException = thrownException ?: mkNewValue(kfgException)
        terms[termException] = kfgException.wrapped()
        concreteValues[termException] = exceptionDescriptor

        val predicate = path(kfgException.location) {
            catch(termException)
        }

        thrownException = null

        processPath(kfgException, predicate)
        postProcess(kfgException, predicate)
    }

    override fun cmp(
        value: String,
        lhv: String,
        rhv: String,
        concreteLhv: Any?,
        concreteRhv: Any?
    ) = safeCall {
        val kfgValue = parseValue(value) as CmpInst
        preProcess(kfgValue)

        val kfgLhv = parseValue(lhv)
        val kfgRhv = parseValue(rhv)

        val termValue = mkNewValue(kfgValue)
        val termLhv = mkValue(kfgLhv)
        val termRhv = mkValue(kfgRhv)

        terms[termValue] = kfgValue.wrapped()
        concreteValues[termValue] = concreteLhv.cmp(kfgValue.opcode, concreteRhv).getAsDescriptor(termValue.type)

        termLhv.updateInfo(kfgLhv, concreteLhv.getAsDescriptor(termLhv.type))
        termRhv.updateInfo(kfgRhv, concreteRhv.getAsDescriptor(termRhv.type))

        val predicate = state(kfgValue.location) {
            termValue equality termLhv.apply(kfgValue.opcode, termRhv)
        }

        postProcess(kfgValue, predicate)
    }

    override fun enterMonitor(
        inst: String,
        operand: String,
        concreteOperand: Any?
    ) = safeCall {
        val instruction = parseValue(inst) as EnterMonitorInst
        preProcess(instruction)

        val kfgMonitor = parseValue(operand)
        val termMonitor = mkValue(kfgMonitor)
        termMonitor.updateInfo(kfgMonitor, concreteOperand.getAsDescriptor(termMonitor.type))

        val predicate = state(instruction.location) {
            enterMonitor(termMonitor)
        }

        postProcess(instruction, predicate)
    }

    override fun exitMonitor(
        inst: String,
        operand: String,
        concreteOperand: Any?
    ) = safeCall {
        val instruction = parseValue(inst) as ExitMonitorInst
        preProcess(instruction)

        val kfgMonitor = parseValue(operand)
        val termMonitor = mkValue(kfgMonitor)
        termMonitor.updateInfo(kfgMonitor, concreteOperand.getAsDescriptor(termMonitor.type))

        val predicate = state(instruction.location) {
            exitMonitor(termMonitor)
        }

        postProcess(instruction, predicate)
    }

    override fun fieldLoad(
        value: String,
        owner: String?,
        klass: String,
        field: String,
        type: String,
        concreteValue: Any?,
        concreteOwner: Any?
    ) = safeCall {
        val kfgValue = parseValue(value) as FieldLoadInst
        preProcess(kfgValue)

        val kfgOwner = owner?.let { parseValue(it) }
        val kfgField = cm[klass].getField(field, parseDesc(cm.type, type))

        val termValue = mkNewValue(kfgValue)
        val termOwner = kfgOwner?.let { mkValue(it) }

        terms[termValue] = kfgValue.wrapped()
        concreteValues[termValue] = concreteValue.getAsDescriptor(termValue.type)

        termOwner?.apply { this.updateInfo(kfgOwner, concreteOwner.getAsDescriptor(termOwner.type)) }

        val predicate = state(kfgValue.location) {
            val actualOwner = termOwner ?: `class`(kfgField.klass)
            termValue equality actualOwner.field(kfgField.type.kexType, kfgField.name).load()
        }

        postProcess(kfgValue, predicate)
    }

    override fun fieldStore(
        inst: String,
        owner: String?,
        klass: String,
        field: String,
        type: String,
        value: String,
        concreteValue: Any?,
        concreteOwner: Any?
    ) = safeCall {
        checkCall()
        val instruction = parseValue(inst) as FieldStoreInst
        preProcess(instruction)

        val kfgOwner = owner?.let { parseValue(it) }
        val kfgField = cm[klass].getField(field, parseDesc(cm.type, type))
        val kfgValue = parseValue(value)

        val termOwner = kfgOwner?.let { mkValue(it) }
        val termValue = mkValue(kfgValue)

        termOwner?.apply { this.updateInfo(kfgOwner, concreteOwner.getAsDescriptor(termOwner.type)) }
        termValue.updateInfo(kfgValue, concreteValue.getAsDescriptor(termValue.type))

        val predicate = state(instruction.location) {
            val actualOwner = termOwner ?: `class`(kfgField.klass)
            actualOwner.field(kfgField.type.kexType, kfgField.name).store(termValue)
        }

        postProcess(instruction, predicate)
    }

    override fun instanceOf(
        value: String,
        operand: String,
        concreteValue: Any?,
        concreteOperand: Any?
    ) = safeCall {
        val kfgValue = parseValue(value) as InstanceOfInst
        preProcess(kfgValue)

        val kfgOperand = parseValue(operand)

        val termValue = mkNewValue(kfgValue)
        val termOperand = mkValue(kfgOperand)

        terms[termValue] = kfgValue.wrapped()
        concreteValues[termValue] = concreteValue.getAsDescriptor(termValue.type)

        termOperand.updateInfo(kfgOperand, concreteOperand.getAsDescriptor(termOperand.type))

        val predicate = state(kfgValue.location) {
            termValue equality (termOperand `is` kfgValue.targetType.kexType)
        }

        postProcess(kfgValue, predicate)
    }

    override fun invokeDynamic(
        value: String,
        operands: List<String>,
        concreteValue: Any?,
        concreteOperands: List<Any?>
    ) {
        val kfgValue = parseValue(value) as InvokeDynamicInst
        preProcess(kfgValue)

        val kfgOperands = operands.map { parseValue(it) }

        val termValue = mkNewValue(kfgValue)
        val termOperands = kfgOperands.map { mkValue(it) }

        terms[termValue] = kfgValue.wrapped()
        concreteValues[termValue] = concreteValue.getAsDescriptor(termValue.type)

        termOperands.withIndex().forEach { (index, term) ->
            term.updateInfo(kfgOperands[index], concreteOperands[index].getAsDescriptor(term.type))
        }

        val predicate = state(kfgValue.location) {
            val lambdaBases = kfgValue.bootstrapMethodArgs.filterIsInstance<Handle>()
            ktassert(lambdaBases.size == 1) { log.error("Unknown number of bases of ${kfgValue.print()}") }
            val lambdaBase = lambdaBases.first()
            val argParameters = lambdaBase.method.argTypes.withIndex().map { arg(it.value.kexType, it.index) }
            val lambdaParameters = lambdaBase.method.argTypes.withIndex().map { (index, type) ->
                term { value(type.kexType, "labmda_${lambdaBase.method.name}_$index") }
            }

            val psa = PredicateStateAnalysis(cm)
            val lambdaBody = psa.builder(lambdaBase.method).methodState
                ?: return log.error("Could not process ${kfgValue.print()}")

            termValue equality lambda(kfgValue.type.kexType, lambdaParameters) {
                TermRenamer(".labmda.${lambdaBase.method.name}", argParameters.zip(lambdaParameters).toMap())
                    .apply(lambdaBody)
            }
        }

        postProcess(kfgValue, predicate)
    }

    override fun jump(
        inst: String
    ) = safeCall {
        val instruction = parseValue(inst) as JumpInst
        preProcess(instruction)

        /**
         * we do not use 'postProcess' here because jump instruction is not converted into any predicate
         */
        previousBlock = instruction.parent
        traceBuilder += instruction
        updateCatches(instruction)
    }

    override fun newArray(
        value: String,
        dimensions: List<String>,
        concreteValue: Any?,
        concreteDimensions: List<Any?>
    ) = safeCall {
        val kfgValue = parseValue(value) as NewArrayInst
        preProcess(kfgValue)

        val kfgDimensions = dimensions.map { parseValue(it) }

        val termValue = mkNewValue(kfgValue)
        val termDimensions = kfgDimensions.map { mkValue(it) }

        terms[termValue] = kfgValue.wrapped()
        concreteValues[termValue] = concreteValue.getAsDescriptor(termValue.type)

        termDimensions.withIndex().forEach { (index, term) ->
            term.updateInfo(kfgDimensions[index], concreteDimensions[index].getAsDescriptor(term.type))
        }

        val predicate = state(kfgValue.location) {
            termValue.new(termDimensions)
        }

        postProcess(kfgValue, predicate)
    }

    override fun new(
        value: String
    ) = safeCall {
        val kfgValue = parseValue(value) as NewInst
        preProcess(kfgValue)

        val termValue = mkNewValue(kfgValue)
        terms[termValue] = kfgValue.wrapped()

        val predicate = state(kfgValue.location) {
            termValue.new()
        }

        postProcess(kfgValue, predicate)
    }

    override fun phi(
        value: String,
        concreteValue: Any?
    ) = safeCall {
        val kfgValue = parseValue(value) as PhiInst
        preProcess(kfgValue)

        val kfgIncoming = kfgValue.incomings[previousBlock]!!

        val termValue = mkNewValue(kfgValue)
        val termIncoming = mkValue(kfgIncoming)

        terms[termValue] = kfgValue.wrapped()
        terms[termIncoming] = kfgIncoming.wrapped()
        concreteValues[termValue] = concreteValue.getAsDescriptor(termValue.type)

        val predicate = state(kfgValue.location) {
            termValue equality termIncoming
        }

        postProcess(kfgValue, predicate)
    }

    override fun ret(
        inst: String,
        returnValue: String?,
        concreteValue: Any?
    ) = safeCall {
        val instruction = parseValue(inst) as ReturnInst
        preProcess(instruction)

        val kfgReturn = returnValue?.let { parseValue(it) }
        val termReturn = kfgReturn?.let { mkValue(it) }

        val stack = frames.pop()
        val receiver = stack.returnReceiver
        if (termReturn != null && receiver != null) {
            val (kfgReceiver, termReceiver) = receiver
            val predicate = state(instruction.location) {
                termReceiver equality termReturn
            }
            terms[termReceiver] = kfgReceiver.wrapped()
            concreteValues[termReceiver] = concreteValue.getAsDescriptor(termReceiver.type)

            predicates[predicate] = instruction
            builder += predicate
        }

        /**
         * we do not use 'postProcess' here because return inst is not always converted into predicate
         */
        traceBuilder += instruction
        updateCatches(instruction)
    }

    override fun switch(
        inst: String,
        value: String,
        concreteValue: Any?
    ) = safeCall {
        val instruction = parseValue(inst) as SwitchInst
        preProcess(instruction)

        val kfgValue = parseValue(value)
        val termValue = mkValue(kfgValue)

        val intValue = concreteValue as Int
        val kfgConstant = ctx.values.getInt(intValue)
        termValue.updateInfo(kfgValue, concreteValue.getAsDescriptor(termValue.type))

        val predicate = path(instruction.location) {
            if (kfgConstant in instruction.branches) termValue equality intValue
            else termValue `!in` instruction.branches.keys.map { value(it) }
        }

        processPath(instruction, predicate)
        postProcess(instruction, predicate)
    }

    override fun tableSwitch(
        inst: String,
        value: String,
        concreteValue: Any?
    ) = safeCall {
        val instruction = parseValue(inst) as TableSwitchInst
        preProcess(instruction)

        val kfgValue = parseValue(value)
        val termValue = mkValue(kfgValue)

        val intValue = concreteValue as Int
        termValue.updateInfo(kfgValue, concreteValue.getAsDescriptor(termValue.type))

        val predicate = path(instruction.location) {
            termValue equality intValue
        }

        processPath(instruction, predicate)
        postProcess(instruction, predicate)
    }

    override fun throwing(
        inst: String,
        exception: String,
        concreteException: Any?
    ) = safeCall {
        val instruction = parseValue(inst) as ThrowInst
        preProcess(instruction)

        val kfgException = parseValue(exception)
        val termException = mkValue(kfgException)

        termException.updateInfo(kfgException, concreteException.getAsDescriptor(termException.type))

        val predicate = path(instruction.location) {
            `throw`(termException)
        }

        thrownException = termException

        processPath(instruction, predicate)
        postProcess(instruction, predicate)
    }

    override fun unary(
        value: String,
        operand: String,
        concreteValue: Any?,
        concreteOperand: Any?
    ) = safeCall {
        val kfgValue = parseValue(value) as UnaryInst
        preProcess(kfgValue)

        val kfgOperand = parseValue(operand)

        val termValue = mkNewValue(kfgValue)
        val termOperand = mkValue(kfgOperand)

        terms[termValue] = kfgValue.wrapped()
        concreteValues[termValue] = concreteValue.getAsDescriptor(termValue.type)

        termOperand.updateInfo(kfgOperand, concreteOperand.getAsDescriptor(termOperand.type))

        val predicate = state(kfgValue.location) {
            termValue equality termOperand.apply(kfgValue.opcode)
        }

        postProcess(kfgValue, predicate)
    }
}