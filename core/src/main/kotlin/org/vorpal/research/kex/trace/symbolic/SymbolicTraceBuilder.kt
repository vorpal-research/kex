@file:Suppress("unused", "DuplicatedCode")

package org.vorpal.research.kex.trace.symbolic

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.state.asTermExpr
import org.vorpal.research.kex.descriptor.*
import org.vorpal.research.kex.ktype.*
import org.vorpal.research.kex.parameters.Parameters
import org.vorpal.research.kex.state.predicate.Predicate
import org.vorpal.research.kex.state.predicate.path
import org.vorpal.research.kex.state.predicate.state
import org.vorpal.research.kex.state.term.NullTerm
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.state.term.term
import org.vorpal.research.kex.state.transformer.TermRenamer
import org.vorpal.research.kex.util.cmp
import org.vorpal.research.kex.util.next
import org.vorpal.research.kex.util.parseValue
import org.vorpal.research.kex.util.parseValueOrNull
import org.vorpal.research.kfg.ir.*
import org.vorpal.research.kfg.ir.value.*
import org.vorpal.research.kfg.ir.value.instruction.*
import org.vorpal.research.kfg.type.SystemTypeNames
import org.vorpal.research.kfg.type.Type
import org.vorpal.research.kfg.type.parseDescOrNull
import org.vorpal.research.kfg.type.parseStringToType
import org.vorpal.research.kthelper.KtException
import org.vorpal.research.kthelper.assert.ktassert
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.collection.stackOf
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.toInt
import org.vorpal.research.kthelper.`try`

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
    companion object {
        private const val MAX_ARRAY_LENGTH = 10000
    }

    /**
     * required fields
     */
    override val clauses: ClauseState
        get() = stateBuilder.toClauseState()
    override val path: PathCondition
        get() = pathBuilder.toPathCondition()
    override val concreteValueMap: Map<Term, Descriptor>
        get() = concreteValues.toMap()
    override val termMap: Map<Term, WrappedValue>
        get() = terms.toMap()

    override val symbolicState: SymbolicState
        get() {
            checkCall()
            return SymbolicStateImpl(
                clauses,
                path,
                concreteValueMap,
                termMap
            )
        }

    /**
     * mutable backing fields for required fields
     */
    private val cm get() = ctx.cm
    private val converter = Object2DescriptorConverter()
    private val stateBuilder = arrayListOf<Clause>()
    private val traceBuilder = arrayListOf<Instruction>()
    private val pathBuilder = arrayListOf<PathClause>()
    private val concreteValues = mutableMapOf<Term, Descriptor>()
    private val terms = mutableMapOf<Term, WrappedValue>()

    private val nullChecked = mutableSetOf<Term>()
    private val typeChecked = mutableMapOf<Term, Type>()
    private val indexChecked = mutableMapOf<Term, MutableSet<Term>>()
    private val lengthChecked = mutableSetOf<Term>()

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

    /**
     * call stack info for collecting the right trace
     */
    private val callStack = stackOf<CallFrame>()
    private val traceCollectingEnabled get() = callStack.isEmpty()

    override fun plus(other: SymbolicState): SymbolicState = symbolicState + other

    private data class CallFrame(val call: CallInst, val next: Instruction)

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
        var previousBlock = method.body.entry

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

    override fun toString() = "$clauses"

    private fun String.toType() = parseDescOrNull(cm.type, this)!!

    private fun safeCall(body: () -> Unit) = `try` {
        body()
    }.getOrThrow {
        SymbolicTraceException("", this)
    }

    private fun addToCallTrace(call: CallInst) {
        callStack.push(CallFrame(call, call.next!!))
    }

    private fun popFromCallTrace() {
        if (callStack.isNotEmpty()) callStack.pop()
    }

    private fun preCheck(name: String) {
        if (callStack.isEmpty()) return
        val (currentCall, expectedNext) = callStack.peek()
        when (val currentInst = parseValueOrNull(name)) {
            currentCall -> addToCallTrace(currentCall)
            expectedNext -> popFromCallTrace()
            is ReturnInst -> {
                ktassert(frames.peek().method == currentInst.parent.method)
                frames.pop()
            }
        }
    }

    private fun parseMethod(className: String, methodName: String, args: List<String>, retType: String): Method {
        val klass = cm[className]
        return klass.getMethod(methodName, MethodDescriptor(args.map { it.toType() }, retType.toType()))
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

    private fun parseValueOrNull(valueName: String): Value? {
        val nm = nameMapperContext.getMapper(currentMethod)
        return nm.parseValueOrNull(valueName)
    }

    private fun newValue(value: Value) = term {
        when (value) {
            is Argument -> arg(value)
            is Constant -> const(value)
            is ThisRef -> `this`(value.type.kexType)
            else -> termFactory.getValue(value.type.kexType, nameGenerator.nextName("${value.name}"))
        }
    }

    private fun mkValue(value: Value) = valueMap.getOrPut(value) { newValue(value) }

    private fun mkNewValue(value: Value): Term {
        val v = newValue(value)
        valueMap[value] = v
        return v
    }

    private fun Descriptor.unwrapped(type: KexType) = when (type) {
        is KexInteger, is KexReal -> when (this) {
            is ObjectDescriptor -> when {
                type is KexBool && this.type == KexClass(SystemTypeNames.integerClass) -> {
                    val value = this["value", KexInt]!! as ConstantDescriptor.Int
                    if (value.value == 0) descriptor { const(false) }
                    else descriptor { const(true) }
                }
                type is KexInt && this.type == KexClass(SystemTypeNames.booleanClass) -> {
                    val value = this["value", KexBool]!! as ConstantDescriptor.Bool
                    if (value.value) descriptor { const(1) }
                    else descriptor { const(0) }
                }
                type is KexInt && this.type == KexClass(SystemTypeNames.charClass) -> {
                    val value = this["value", KexChar]!! as ConstantDescriptor.Char
                    descriptor { const(value.value.code) }
                }
                type is KexInt && this.type == KexClass(SystemTypeNames.longClass) -> {
                    val value = this["value", KexLong]!! as ConstantDescriptor.Long
                    descriptor { const(value.value.toInt()) }
                }
                type is KexLong && this.type == KexClass(SystemTypeNames.longClass) -> {
                    val value = this["value", KexLong]!! as ConstantDescriptor.Long
                    descriptor { const(value.value) }
                }
                type is KexChar && this.type == KexClass(SystemTypeNames.integerClass) -> {
                    val value = this["value", KexInt]!! as ConstantDescriptor.Int
                    descriptor { const(value.value.toChar()) }
                }
                else -> this["value", type]
                    ?: unreachable { log.error("Unknown descriptor for type $type: $this") }
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
            if (!(method overrides call.method)) {
                checkCall()
                return@safeCall
            }
            popFromCallTrace()

            for ((value, term) in method.parameterValues.asList.zip(call.params.asList)) {
                valueMap[value] = term
            }
            lastCall = null
        } else {
            if (!traceCollectingEnabled) return@safeCall

            for ((index, argType) in method.argTypes.withIndex()) {
                val argValue = cm.value.getArgument(index, method, argType)
                val argTerm = mkNewValue(argValue)
                concreteValues[argTerm] = args[index].getAsDescriptor()
            }
        }
    }

    private fun checkCall() = safeCall {
        lastCall?.let {
            stateBuilder += StateClause(it.call, it.predicate)
        }
        lastCall = null
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
        postProcess(StateClause(instruction, predicate))
    }

    @Suppress("SameParameterValue")
    private fun postProcess(type: PathClauseType, instruction: Instruction, predicate: Predicate) {
        postProcess(PathClause(type, instruction, predicate))
    }

    private fun postProcess(clause: Clause) {
        stateBuilder += clause
        traceBuilder += clause.instruction
        updateCatches(clause.instruction)
    }

    private fun processPath(type: PathClauseType, instruction: Instruction, predicate: Predicate) {
        previousBlock = instruction.parent
        pathBuilder += PathClause(type, instruction, predicate)
    }

    private fun restoreCatchFrame(exceptionType: Type) {
        do {
            val frame = currentFrame
            val candidates = frame.catchMap.keys.filter { exceptionType.isSubtypeOf(it) }
            val candidate = candidates.find { candidate -> candidates.all { candidate.isSubtypeOf(it) } }

            if (callStack.isNotEmpty() && frames.peek().method overrides callStack.peek().call.parent.method) {
                callStack.pop()
            }

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
        preCheck(value)
        if (!traceCollectingEnabled) return@safeCall

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
        preCheck(inst)
        if (!traceCollectingEnabled) return@safeCall

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
        preCheck(value)
        if (!traceCollectingEnabled) return@safeCall

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
        preCheck(inst)
        if (!traceCollectingEnabled) return@safeCall

        val instruction = parseValue(inst) as BranchInst
        preProcess(instruction)

        val kfgCondition = parseValue(condition)
        val termCondition = mkValue(kfgCondition)
        val booleanValue = (concreteValues[termCondition] as? ConstantDescriptor.Bool)?.value
            ?: unreachable { log.error("Unknown boolean value in branch") }

        val predicate = path(instruction.location) {
            termCondition equality booleanValue
        }

        processPath(PathClauseType.CONDITION_CHECK, instruction, predicate)
        postProcess(PathClauseType.CONDITION_CHECK, instruction, predicate)
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
        preCheck(inst)
        if (!traceCollectingEnabled) return@safeCall

        val instruction = parseValue(inst) as CallInst
        preProcess(instruction)
        addToCallTrace(instruction)

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
            val actualCallee = termCallee ?: staticRef(calledMethod.klass)
            when {
                termReturn != null -> termReturn.call(actualCallee.call(calledMethod, termArguments))
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
        preCheck(value)
        if (!traceCollectingEnabled) return@safeCall

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

//        preCheck(exception)
        if (!traceCollectingEnabled) return@safeCall

        val kfgException = parseValue(exception) as CatchInst
        preProcess(kfgException)

        val termException = thrownException ?: mkNewValue(kfgException)
        terms[termException] = kfgException.wrapped()
        concreteValues[termException] = exceptionDescriptor

        val predicate = state(kfgException.location) {
            catch(termException)
        }

        thrownException = null

        postProcess(kfgException, predicate)
    }

    override fun cmp(
        value: String,
        lhv: String,
        rhv: String,
        concreteLhv: Any?,
        concreteRhv: Any?
    ) = safeCall {
        preCheck(value)
        if (!traceCollectingEnabled) return@safeCall

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
        preCheck(inst)
        if (!traceCollectingEnabled) return@safeCall

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
        preCheck(inst)
        if (!traceCollectingEnabled) return@safeCall

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
        preCheck(value)
        if (!traceCollectingEnabled) return@safeCall

        val kfgValue = parseValue(value) as FieldLoadInst
        preProcess(kfgValue)

        val kfgOwner = owner?.let { parseValue(it) }
        val kfgField = cm[klass].getField(field, parseDescOrNull(cm.type, type)!!)

        val termValue = mkNewValue(kfgValue)
        val termOwner = kfgOwner?.let { mkValue(it) }

        terms[termValue] = kfgValue.wrapped()
        concreteValues[termValue] = concreteValue.getAsDescriptor(termValue.type)

        termOwner?.apply { this.updateInfo(kfgOwner, concreteOwner.getAsDescriptor(termOwner.type)) }

        val predicate = state(kfgValue.location) {
            val actualOwner = termOwner ?: staticRef(kfgField.klass)
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
        preCheck(inst)
        if (!traceCollectingEnabled) return@safeCall

        val instruction = parseValue(inst) as FieldStoreInst
        preProcess(instruction)

        val kfgOwner = owner?.let { parseValue(it) }
        val kfgField = cm[klass].getField(field, parseDescOrNull(cm.type, type)!!)
        val kfgValue = parseValue(value)

        val termOwner = kfgOwner?.let { mkValue(it) }
        val termValue = mkValue(kfgValue)

        termOwner?.apply { this.updateInfo(kfgOwner, concreteOwner.getAsDescriptor(termOwner.type)) }
        termValue.updateInfo(kfgValue, concreteValue.getAsDescriptor(termValue.type))

        val predicate = state(instruction.location) {
            val actualOwner = termOwner ?: staticRef(kfgField.klass)
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
        preCheck(value)
        if (!traceCollectingEnabled) return@safeCall

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
    ) = safeCall {
        preCheck(value)
        if (!traceCollectingEnabled) return@safeCall

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

            val expr = lambdaBase.method.asTermExpr()
                ?: return@safeCall log.error("Could not process ${kfgValue.print()}")

            termValue equality lambda(kfgValue.type.kexType, lambdaParameters) {
                TermRenamer(".labmda.${lambdaBase.method.name}", argParameters.zip(lambdaParameters).toMap())
                    .transformTerm(expr)
            }
        }

        postProcess(kfgValue, predicate)
    }

    override fun jump(
        inst: String
    ) = safeCall {
        preCheck(inst)
        if (!traceCollectingEnabled) return@safeCall

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
        preCheck(value)
        if (!traceCollectingEnabled) return@safeCall

        val kfgValue = parseValue(value) as NewArrayInst
        preProcess(kfgValue)

        val kfgDimensions = dimensions.map { parseValue(it) }

        val termValue = mkNewValue(kfgValue)
        val termDimensions = kfgDimensions.map { mkValue(it) }

        nullChecked += termValue
        typeChecked[termValue] = kfgValue.type
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
        preCheck(value)
        if (!traceCollectingEnabled) return@safeCall

        val kfgValue = parseValue(value) as NewInst
        preProcess(kfgValue)

        val termValue = mkNewValue(kfgValue)
        nullChecked += termValue
        typeChecked[termValue] = kfgValue.type
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
        preCheck(value)
        if (!traceCollectingEnabled) return@safeCall

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
        preCheck(inst)
        if (!traceCollectingEnabled) return@safeCall

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

            stateBuilder += StateClause(instruction, predicate)
        }

        /**
         * we do not use 'postProcess' here because return inst is not always converted into predicate
         */
        traceBuilder += instruction
        updateCatches(instruction)
    }

    private fun numericValue(value: Any?): Number = when (value) {
        is Boolean -> value.toInt()
        is Byte -> value
        is Char -> value.code
        is Short -> value
        is Int -> value
        is Long -> value
        is Float -> value
        is Double -> value
        else -> unreachable { log.error("Could not compute numeric value of $value") }
    }

    override fun switch(
        inst: String,
        value: String,
        concreteValue: Any?
    ) = safeCall {
        preCheck(inst)
        if (!traceCollectingEnabled) return@safeCall

        val instruction = parseValue(inst) as SwitchInst
        preProcess(instruction)

        val kfgValue = parseValue(value)
        val termValue = mkValue(kfgValue)

        val intValue = numericValue(concreteValue).toInt()
        val kfgConstant = ctx.values.getInt(intValue)
        termValue.updateInfo(kfgValue, concreteValue.getAsDescriptor(termValue.type))

        val predicate = path(instruction.location) {
            if (kfgConstant in instruction.branches) termValue equality intValue
            else termValue `!in` instruction.branches.keys.map { value(it) }
        }

        processPath(PathClauseType.CONDITION_CHECK, instruction, predicate)
        postProcess(PathClauseType.CONDITION_CHECK, instruction, predicate)
    }

    override fun tableSwitch(
        inst: String,
        value: String,
        concreteValue: Any?
    ) = safeCall {
        preCheck(inst)
        if (!traceCollectingEnabled) return@safeCall

        val instruction = parseValue(inst) as TableSwitchInst
        preProcess(instruction)

        val kfgValue = parseValue(value)
        val termValue = mkValue(kfgValue)

        val intValue = concreteValue as Int
        termValue.updateInfo(kfgValue, concreteValue.getAsDescriptor(termValue.type))

        val predicate = path(instruction.location) {
            termValue equality intValue
        }

        processPath(PathClauseType.CONDITION_CHECK, instruction, predicate)
        postProcess(PathClauseType.CONDITION_CHECK, instruction, predicate)
    }

    override fun throwing(
        inst: String,
        exception: String,
        concreteException: Any?
    ) = safeCall {
        preCheck(inst)
        if (!traceCollectingEnabled) return@safeCall

        val instruction = parseValue(inst) as ThrowInst
        preProcess(instruction)

        val kfgException = parseValue(exception)
        val termException = mkValue(kfgException)

        termException.updateInfo(kfgException, concreteException.getAsDescriptor(termException.type))

        val predicate = state(instruction.location) {
            `throw`(termException)
        }

        thrownException = termException

        postProcess(instruction, predicate)
    }

    override fun unary(
        value: String,
        operand: String,
        concreteValue: Any?,
        concreteOperand: Any?
    ) = safeCall {
        preCheck(value)
        if (!traceCollectingEnabled) return@safeCall

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

    override fun addNullityConstraints(inst: String, value: String, concreteValue: Any?) = safeCall {
        preCheck(inst)
        if (!traceCollectingEnabled) return@safeCall

        val instruction = parseValue(inst) as Instruction

        val kfgValue = parseValue(value)
        val termValue = mkValue(kfgValue)

        if (kfgValue is ThisRef) return@safeCall
        else if (termValue in nullChecked) return@safeCall
        else if (termValue is NullTerm) return@safeCall
        nullChecked += termValue

        val checkName = term { value(KexBool, "${termValue}NullCheck") }
        val checkPredicate = state { checkName equality (termValue eq null) }

        stateBuilder += StateClause(instruction, checkPredicate)


        val pathPredicate = path {
            when (concreteValue) {
                null -> checkName equality true
                else -> checkName equality false
            }
        }

        processPath(PathClauseType.NULL_CHECK, instruction, pathPredicate)
        stateBuilder += PathClause(PathClauseType.NULL_CHECK, instruction, pathPredicate)
    }

    override fun addTypeConstraints(inst: String, value: String, concreteValue: Any?) = safeCall {
        preCheck(inst)
        if (!traceCollectingEnabled) return@safeCall

        if (concreteValue == null) return@safeCall

        val instruction = parseValue(inst) as Instruction

        val kfgValue = parseValue(value)
        val termValue = mkValue(kfgValue)
        val descriptorValue = concreteValue.getAsDescriptor()
        val kfgType = descriptorValue.type.getKfgType(ctx.types)
        if (termValue in typeChecked) {
            val checkedType = typeChecked.getValue(termValue)
            if (checkedType.isSubtypeOf(kfgType)) return@safeCall
        }
        typeChecked[termValue] = kfgType

        val predicate = path {
            (termValue `is` descriptorValue.type) equality true
        }

        processPath(PathClauseType.OVERLOAD_CHECK, instruction, predicate)
        stateBuilder += PathClause(PathClauseType.OVERLOAD_CHECK, instruction, predicate)
    }

    override fun addTypeConstraints(inst: String, value: String, type: String, concreteValue: Any?) = safeCall {
        preCheck(inst)
        if (!traceCollectingEnabled) return@safeCall

        val instruction = parseValue(inst) as Instruction

        val kfgValue = parseValue(value)
        val termValue = mkValue(kfgValue)
        val expectedKfgType = parseStringToType(cm.type, type)
        val comparisonResult = when (concreteValue) {
            null -> false
            else -> {
                val descriptorValue = concreteValue.getAsDescriptor()
                val actualKfgType = descriptorValue.type.getKfgType(ctx.types)
                actualKfgType.isSubtypeOf(expectedKfgType)
            }
        }
        if (kfgValue is NullConstant) return@safeCall
        if (termValue in typeChecked) {
            val checkedType = typeChecked.getValue(termValue)
            if (checkedType.isSubtypeOf(expectedKfgType)) return@safeCall
        }
        typeChecked[termValue] = expectedKfgType

        val predicate = path {
            (termValue `is` expectedKfgType.kexType) equality comparisonResult
        }

        processPath(PathClauseType.TYPE_CHECK, instruction, predicate)
        stateBuilder += PathClause(PathClauseType.TYPE_CHECK, instruction, predicate)
    }

    override fun addArrayIndexConstraints(
        inst: String,
        array: String,
        index: String,
        concreteArray: Any?,
        concreteIndex: Any?
    ) = safeCall {
        preCheck(inst)
        if (!traceCollectingEnabled) return@safeCall

        if (concreteArray == null) return@safeCall

        val instruction = parseValue(inst) as Instruction

        val kfgArray = parseValue(array)
        val kfgIndex = parseValue(index)

        val termArray = mkValue(kfgArray)
        val termIndex = mkValue(kfgIndex)

        if (termIndex in indexChecked.getOrPut(termArray, ::mutableSetOf)) return@safeCall
        indexChecked[termArray]!!.add(termIndex)

        val actualLength = concreteArray.arraySize
        val actualIndex = (concreteIndex as? Int) ?: return@safeCall

        val checkTerm = term { value(KexBool, "${termArray}IndexCheck${termIndex}") }
        val checkPredicate = state {
            checkTerm equality (termIndex lt termArray.length())
        }

        stateBuilder += StateClause(instruction, checkPredicate)

        val pathPredicate = path {
            checkTerm equality (actualIndex < actualLength)
        }

        processPath(PathClauseType.BOUNDS_CHECK, instruction, pathPredicate)
        stateBuilder += PathClause(PathClauseType.BOUNDS_CHECK, instruction, pathPredicate)
    }

    override fun addArrayLengthConstraints(
        inst: String,
        length: String,
        concreteLength: Any?
    ) = safeCall {
        preCheck(inst)
        if (!traceCollectingEnabled) return@safeCall

        val instruction = parseValue(inst) as Instruction

        val kfgLength = parseValue(length)
        if (kfgLength is Constant) return@safeCall

        val termLength = mkValue(kfgLength)

        if (termLength in lengthChecked) return@safeCall

        val actualLength = (concreteLength as? Int) ?: return@safeCall

        val positiveCheckTerm = term { value(KexBool, "${termLength}PositiveLengthCheck") }
        val positiveCheckPredicate = state {
            positiveCheckTerm equality (termLength ge 0)
        }

        stateBuilder += StateClause(instruction, positiveCheckPredicate)

        val positivePathPredicate = path {
            positiveCheckTerm equality (actualLength >= 0)
        }

        processPath(PathClauseType.BOUNDS_CHECK, instruction, positivePathPredicate)
        stateBuilder += PathClause(PathClauseType.BOUNDS_CHECK, instruction, positivePathPredicate)

        val upperBoundCheckTerm = term { value(KexBool, "${termLength}UpperBoundLengthCheck") }
        val upperBoundCheckPredicate = state {
            upperBoundCheckTerm equality (termLength lt MAX_ARRAY_LENGTH)
        }

        stateBuilder += StateClause(instruction, upperBoundCheckPredicate)

        val upperBoundPathPredicate = path {
            upperBoundCheckTerm equality (actualLength < MAX_ARRAY_LENGTH)
        }

        processPath(PathClauseType.BOUNDS_CHECK, instruction, upperBoundPathPredicate)
        stateBuilder += PathClause(PathClauseType.BOUNDS_CHECK, instruction, upperBoundPathPredicate)
    }

    private val Any.arraySize: Int
        get() = when (this) {
            is BooleanArray -> this.size
            is ByteArray -> this.size
            is CharArray -> this.size
            is ShortArray -> this.size
            is IntArray -> this.size
            is LongArray -> this.size
            is FloatArray -> this.size
            is DoubleArray -> this.size
            is Array<*> -> this.size
            else -> 0
        }
}
