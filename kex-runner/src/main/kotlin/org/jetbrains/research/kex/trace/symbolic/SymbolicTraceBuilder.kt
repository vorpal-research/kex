@file:Suppress("unused", "DuplicatedCode")

package org.jetbrains.research.kex.trace.symbolic

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.ktype.*
import org.jetbrains.research.kex.reanimator.descriptor.*
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.StateBuilder
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.predicate.path
import org.jetbrains.research.kex.state.predicate.state
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.term
import org.jetbrains.research.kex.trace.file.UnknownNameException
import org.jetbrains.research.kex.util.cmp
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.ConcreteClass
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.MethodDesc
import org.jetbrains.research.kfg.ir.value.Argument
import org.jetbrains.research.kfg.ir.value.Constant
import org.jetbrains.research.kfg.ir.value.ThisRef
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.instruction.*
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

class SymbolicPathCondition : PathCondition {
    override val path = arrayListOf<Clause>()

    operator fun plusAssign(clause: Clause) {
        path += clause
    }
}

class SymbolicTraceBuilder(val ctx: ExecutionContext) : SymbolicState, InstructionTraceCollector {
    /**
     * required fields
     */
    override val state: PredicateState
        get() = builder.current
    override val path: PathCondition
        get() = pathBuilder
    override val concreteValueMap: Map<Term, Descriptor>
        get() = concreteValues
    override val termMap: Map<Term, Value>
        get() = terms
    override val predicateMap: Map<Predicate, Instruction>
        get() = predicates

    override val symbolicState: SymbolicState
        get() = this
    override val trace: List<Instruction>
        get() = traceBuilder

    /**
     * mutable backing fields for required fields
     */
    private val cm get() = ctx.cm
    private val converter = Object2DescriptorConverter()
    private val builder = StateBuilder()
    private val traceBuilder = arrayListOf<Instruction>()
    private val pathBuilder = SymbolicPathCondition()
    private val concreteValues = mutableMapOf<Term, Descriptor>()
    private val terms = mutableMapOf<Term, Value>()
    private val predicates = mutableMapOf<Predicate, Instruction>()

    /**
     * stack frame info for method
     */
    private val frames = stackOf<StackFrame>()
//    private val methodStack = stackOf<Method>()
//    private val frameMap = stackOf<MutableMap<Value, Term>>(mutableMapOf())
//    private val returnReceivers = stackOf<Pair<Value, Term>?>()

    private val nameGenerator = NameGenerator()
    private val currentMethod get() = frames.peek().method
    private val valueMap get() = frames.peek().valueMap

    /**
     * necessary runtime info
     */
    private var lastCall: Call? = null
    private var thrownException: Term? = null
    private lateinit var previousBlock: BasicBlock

    private class StackFrame(
        val method: Method,
        val valueMap: MutableMap<Value, Term>,
        val returnReceiver: Pair<Value, Term>?
    )

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
        val params: Map<Value, Term>,
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
        val st = currentMethod.slotTracker
        return st.getBlock(blockName) ?: throw UnknownNameException(blockName)
    }

    private fun parseValue(valueName: String): Value {
        val st = currentMethod.slotTracker
        return st.getValue(valueName) ?: when {
            valueName.matches(Regex("\\d+")) -> cm.value.getInt(valueName.toInt())
            valueName.matches(Regex("\\d+.\\d+")) -> cm.value.getDouble(valueName.toDouble())
            valueName.matches(Regex("-\\d+")) -> cm.value.getInt(valueName.toInt())
            valueName.matches(Regex("-\\d+.\\d+")) -> cm.value.getDouble(valueName.toDouble())
            valueName.matches(Regex("\".*\"")) -> cm.value.getString(
                valueName.substring(
                    1,
                    valueName.lastIndex
                )
            )
            valueName.matches(Regex("\"[\n\\s]*\"")) -> cm.value.getString(
                valueName.substring(
                    1,
                    valueName.lastIndex
                )
            )
            valueName.matches(Regex(".*(/.*)+.class")) -> cm.value.getClass("L${valueName.removeSuffix(".class")};")
            valueName == "null" -> cm.value.nullConstant
            valueName == "true" -> cm.value.getBool(true)
            valueName == "false" -> cm.value.getBool(false)
            else -> throw UnknownNameException(valueName)
        }
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
                else -> this["value", type]!!
            }
            else -> this
        }
        else -> this
    }

    private fun Term.updateInfo(value: Value, concreteValue: Descriptor) {
        terms.getOrPut(this) { value }
        concreteValues.getOrPut(this) { concreteValue }
    }

    private fun Any?.getAsDescriptor(type: KexType) = converter.convert(this).unwrapped(type)

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
        frames.push(StackFrame(method, mutableMapOf(), lastCall?.receiver))
        if (lastCall != null) {
            val call = lastCall!!
            ktassert(call.method overrides method)

            for ((arg, value) in call.params) {
                valueMap[arg] = value
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

    override fun arrayLoad(
        value: String,
        arrayRef: String,
        index: String,
        concreteValue: Any?,
        concreteRef: Any?,
        concreteIndex: Any?
    ) = safeCall {
        checkCall()

        val kfgValue = parseValue(value) as? ArrayLoadInst
            ?: unreachable { log.error("Value does not correspond to trace") }
        val kfgRef = parseValue(arrayRef)
        val kfgIndex = parseValue(index)

        val termValue = mkNewValue(kfgValue)
        val termRef = mkValue(kfgRef)
        val termIndex = mkValue(kfgIndex)

        terms[termValue] = kfgValue
        concreteValues[termValue] = concreteValue.getAsDescriptor(termValue.type)

        termRef.updateInfo(kfgRef, concreteRef.getAsDescriptor(termRef.type))
        termIndex.updateInfo(kfgIndex, concreteIndex.getAsDescriptor(termIndex.type))

        val predicate = state(kfgValue.location) {
            termValue equality termRef[termIndex].load()
        }
        predicates[predicate] = kfgValue
        builder += predicate

        traceBuilder += kfgValue
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
        checkCall()

        val instruction = parseValue(inst) as? ArrayStoreInst
            ?: unreachable { log.error("Value does not correspond to trace") }

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
        predicates[predicate] = instruction
        builder += predicate

        traceBuilder += instruction
    }

    override fun binary(
        value: String,
        lhv: String,
        rhv: String,
        concreteValue: Any?,
        concreteLhv: Any?,
        concreteRhv: Any?
    ) = safeCall {
        checkCall()

        val kfgValue = parseValue(value) as? BinaryInst
            ?: unreachable { log.error("Value does not correspond to trace") }
        val kfgLhv = parseValue(lhv)
        val kfgRhv = parseValue(rhv)

        val termValue = mkNewValue(kfgValue)
        val termLhv = mkValue(kfgLhv)
        val termRhv = mkValue(kfgRhv)

        terms[termValue] = kfgValue
        concreteValues[termValue] = concreteValue.getAsDescriptor(termValue.type)

        termLhv.updateInfo(kfgLhv, concreteLhv.getAsDescriptor(termLhv.type))
        termRhv.updateInfo(kfgRhv, concreteRhv.getAsDescriptor(termRhv.type))

        val predicate = state(kfgValue.location) {
            termValue equality termLhv.apply(cm.type, kfgValue.opcode, termRhv)
        }
        predicates[predicate] = kfgValue
        builder += predicate

        traceBuilder += kfgValue
    }

    override fun branch(
        inst: String,
        condition: String
    ) = safeCall {
        checkCall()
        val instruction = parseValue(inst) as? BranchInst
            ?: unreachable { log.error("Value does not correspond to trace") }

        val kfgCondition = parseValue(condition)
        val termCondition = mkValue(kfgCondition)
        val booleanValue = (concreteValues[termCondition] as? ConstantDescriptor.Bool)?.value
            ?: unreachable { log.error("Unknown boolean value in branch") }

        val predicate = path(instruction.location) {
            termCondition equality booleanValue
        }
        predicates[predicate] = instruction
        builder += predicate

        previousBlock = instruction.parent

        traceBuilder += instruction
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
        checkCall()
        val instruction = parseValue(inst) as? CallInst
            ?: unreachable {
                parseValue(inst)
                log.error("Value does not correspond to trace")
            }

        val calledMethod = parseMethod(className, methodName, argTypes, retType)
        val kfgReturn = returnValue?.let { parseValue(it) }
        val kfgCallee = callee?.let { parseValue(it) }
        val kfgArguments = arguments.map { parseValue(it) }

        val termReturn = kfgReturn?.let { mkNewValue(it) }
        val termCallee = kfgCallee?.let { mkValue(it) }
        val termArguments = kfgArguments.map { mkValue(it) }

        termReturn?.apply { terms[this] = kfgReturn }

        termCallee?.apply {
            terms.getOrPut(this) { kfgCallee }
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
            termArguments.withIndex().associate { (index, term) ->
                ctx.values.getArgument(index, calledMethod, calledMethod.argTypes[index]) to term
            },
            predicate
        )

        traceBuilder += instruction
    }

    override fun cast(
        value: String,
        operand: String,
        concreteValue: Any?,
        concreteOperand: Any?
    ) = safeCall {
        checkCall()

        val kfgValue = parseValue(value) as? CastInst
            ?: unreachable {
                parseValue(value)
                log.error("Value does not correspond to trace")
            }
        val kfgOperand = parseValue(operand)

        val termValue = mkNewValue(kfgValue)
        val termOperand = mkValue(kfgOperand)

        terms[termValue] = kfgValue
        concreteValues[termValue] = concreteValue.getAsDescriptor(termValue.type)

        termOperand.updateInfo(kfgOperand, concreteOperand.getAsDescriptor(termOperand.type))

        val predicate = state(kfgValue.location) {
            termValue equality (termOperand `as` kfgValue.type.kexType)
        }
        predicates[predicate] = kfgValue
        builder += predicate

        traceBuilder += kfgValue
    }

    override fun catch(
        exception: String,
        concreteException: Any?
    ) = safeCall {
        checkCall()

        // todo
        val kfgException = parseValue(exception) as? CatchInst
            ?: unreachable { log.error("Value does not correspond to trace") }
        val termException = thrownException ?: mkNewValue(kfgException)
        terms[termException] = kfgException
        concreteValues[termException] = concreteValues.getAsDescriptor(termException.type)

        val predicate = path(kfgException.location) {
            catch(termException)
        }
        predicates[predicate] = kfgException
        builder += predicate

        traceBuilder += kfgException
        thrownException = null
    }

    override fun cmp(
        value: String,
        lhv: String,
        rhv: String,
        concreteLhv: Any?,
        concreteRhv: Any?
    ) = safeCall {
        checkCall()

        val kfgValue = parseValue(value) as? CmpInst
            ?: unreachable {
                parseValue(value)
                log.error("Value does not correspond to trace")
            }
        val kfgLhv = parseValue(lhv)
        val kfgRhv = parseValue(rhv)

        val termValue = mkNewValue(kfgValue)
        val termLhv = mkValue(kfgLhv)
        val termRhv = mkValue(kfgRhv)

        terms[termValue] = kfgValue
        concreteValues[termValue] = concreteLhv.cmp(kfgValue.opcode, concreteRhv).getAsDescriptor(termValue.type)

        termLhv.updateInfo(kfgLhv, concreteLhv.getAsDescriptor(termLhv.type))
        termRhv.updateInfo(kfgRhv, concreteRhv.getAsDescriptor(termRhv.type))

        val predicate = state(kfgValue.location) {
            termValue equality termLhv.apply(kfgValue.opcode, termRhv)
        }
        predicates[predicate] = kfgValue
        builder += predicate

        traceBuilder += kfgValue
    }

    override fun enterMonitor(
        inst: String,
        operand: String,
        concreteOperand: Any?
    ) = safeCall {
        checkCall()

        // todo
        val instruction = parseValue(inst) as? EnterMonitorInst
            ?: unreachable { log.error("Value does not correspond to trace") }
        traceBuilder += instruction
    }

    override fun exitMonitor(
        inst: String,
        operand: String,
        concreteOperand: Any?
    ) = safeCall {
        checkCall()

        // todo
        val instruction = parseValue(inst) as? EnterMonitorInst
            ?: unreachable { log.error("Value does not correspond to trace") }
        traceBuilder += instruction
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
        checkCall()

        val kfgValue = parseValue(value) as? FieldLoadInst
            ?: unreachable { log.error("Value does not correspond to trace") }
        val kfgOwner = owner?.let { parseValue(it) }
        val kfgField = cm[klass].getField(field, parseDesc(cm.type, type))

        val termValue = mkNewValue(kfgValue)
        val termOwner = kfgOwner?.let { mkValue(it) }

        terms[termValue] = kfgValue
        concreteValues[termValue] = concreteValue.getAsDescriptor(termValue.type)

        termOwner?.apply { this.updateInfo(kfgOwner, concreteOwner.getAsDescriptor(termOwner.type)) }

        val predicate = state(kfgValue.location) {
            val actualOwner = termOwner ?: `class`(kfgField.klass)
            termValue equality actualOwner.field(kfgField.type.kexType, kfgField.name)
        }
        predicates[predicate] = kfgValue
        builder += predicate

        traceBuilder += kfgValue
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
        val instruction = parseValue(inst) as? FieldStoreInst
            ?: unreachable { log.error("Value does not correspond to trace") }

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
        predicates[predicate] = instruction
        builder += predicate

        traceBuilder += instruction
    }

    override fun instanceOf(
        value: String,
        operand: String,
        concreteValue: Any?,
        concreteOperand: Any?
    ) = safeCall {
        checkCall()

        val kfgValue = parseValue(value) as? InstanceOfInst
            ?: unreachable { log.error("Value does not correspond to trace") }
        val kfgOperand = parseValue(operand)

        val termValue = mkNewValue(kfgValue)
        val termOperand = mkValue(kfgOperand)

        terms[termValue] = kfgValue
        concreteValues[termValue] = concreteValue.getAsDescriptor(termValue.type)

        termOperand.updateInfo(kfgOperand, concreteOperand.getAsDescriptor(termOperand.type))

        val predicate = state(kfgValue.location) {
            termValue equality (termOperand `is` kfgValue.targetType.kexType)
        }
        predicates[predicate] = kfgValue
        builder += predicate

        traceBuilder += kfgValue
    }

    override fun jump(
        inst: String
    ) = safeCall {
        checkCall()
        val instruction = parseValue(inst) as? JumpInst
            ?: unreachable {
                parseValue(inst)
                log.error("Value does not correspond to trace")
            }

        previousBlock = instruction.parent
        traceBuilder += instruction
    }

    override fun newArray(
        value: String,
        dimensions: List<String>,
        concreteValue: Any?,
        concreteDimensions: List<Any?>
    ) = safeCall {
        checkCall()

        val kfgValue = parseValue(value) as? NewArrayInst
            ?: unreachable { log.error("Value does not correspond to trace") }
        val kfgDimensions = dimensions.map { parseValue(it) }

        val termValue = mkNewValue(kfgValue)
        val termDimensions = kfgDimensions.map { mkValue(it) }

        terms[termValue] = kfgValue
        concreteValues[termValue] = concreteValue.getAsDescriptor(termValue.type)

        termDimensions.withIndex().forEach { (index, term) ->
            term.updateInfo(kfgDimensions[index], concreteDimensions[index].getAsDescriptor(term.type))
        }

        val predicate = state(kfgValue.location) {
            termValue.new(termDimensions)
        }
        predicates[predicate] = kfgValue
        builder += predicate

        traceBuilder += kfgValue
    }

    override fun new(
        value: String
    ) = safeCall {
        checkCall()

        val kfgValue = parseValue(value) as? NewInst
            ?: unreachable { log.error("Value does not correspond to trace") }

        val termValue = mkNewValue(kfgValue)
        terms[termValue] = kfgValue

        val predicate = state(kfgValue.location) {
            termValue.new()
        }
        predicates[predicate] = kfgValue
        builder += predicate

        traceBuilder += kfgValue
    }

    override fun phi(
        value: String,
        concreteValue: Any?
    ) = safeCall {
        checkCall()

        val kfgValue = parseValue(value) as? PhiInst
            ?: unreachable { log.error("Value does not correspond to trace") }
        val kfgIncoming = kfgValue.incomings[previousBlock]!!

        val termValue = mkNewValue(kfgValue)
        val termIncoming = mkValue(kfgIncoming)

        terms[termValue] = kfgValue
        terms[termIncoming] = kfgIncoming
        concreteValues[termValue] = concreteValue.getAsDescriptor(termValue.type)

        val predicate = state(kfgValue.location) {
            termValue equality termIncoming
        }
        predicates[predicate] = kfgValue
        builder += predicate

        traceBuilder += kfgValue
    }

    override fun ret(
        inst: String,
        returnValue: String?,
        concreteValue: Any?
    ) = safeCall {
        checkCall()
        val instruction = parseValue(inst) as? ReturnInst
            ?: unreachable { log.error("Value does not correspond to trace") }
        val kfgReturn = returnValue?.let { parseValue(it) }

        val termReturn = kfgReturn?.let { mkValue(it) }

        val stack = frames.pop()
        val receiver = stack.returnReceiver
        if (termReturn != null && receiver != null) {
            val (kfgReceiver, termReceiver) = receiver
            val predicate = state(instruction.location) {
                termReceiver equality termReturn
            }
            terms[termReceiver] = kfgReceiver
            concreteValues[termReceiver] = concreteValue.getAsDescriptor(termReceiver.type)

            predicates[predicate] = instruction
            builder += predicate
        }

        traceBuilder += instruction
    }

    override fun switch(
        inst: String,
        value: String,
        concreteValue: Any?
    ) = safeCall {
        checkCall()
        val instruction = parseValue(inst) as? SwitchInst
            ?: unreachable { log.error("Value does not correspond to trace") }

        val kfgValue = parseValue(value)
        val termValue = mkValue(kfgValue)

        val intValue = concreteValue as Int
        termValue.updateInfo(kfgValue, concreteValue.getAsDescriptor(termValue.type))

        val predicate = path(instruction.location) {
            termValue equality intValue
        }
        predicates[predicate] = instruction
        builder += predicate

        previousBlock = instruction.parent
        traceBuilder += instruction
    }

    override fun tableSwitch(
        inst: String,
        value: String,
        concreteValue: Any?
    ) = safeCall {
        checkCall()
        val instruction = parseValue(inst) as? TableSwitchInst
            ?: unreachable { log.error("Value does not correspond to trace") }
        val kfgValue = parseValue(value)
        val termValue = mkValue(kfgValue)

        val intValue = concreteValue as Int
        termValue.updateInfo(kfgValue, concreteValue.getAsDescriptor(termValue.type))

        val predicate = path(instruction.location) {
            termValue equality intValue
        }
        predicates[predicate] = instruction
        builder += predicate

        previousBlock = instruction.parent
        traceBuilder += instruction
    }

    override fun throwing(
        inst: String,
        exception: String,
        concreteException: Any?
    ) = safeCall {
        checkCall()
        val instruction = parseValue(inst) as? ThrowInst
            ?: unreachable { log.error("Value does not correspond to trace") }
        val kfgException = parseValue(exception)
        val termException = mkValue(kfgException)

        termException.updateInfo(kfgException, concreteException.getAsDescriptor(termException.type))

        val predicate = path(instruction.location) {
            `throw`(termException)
        }
        predicates[predicate] = instruction
        builder += predicate
        previousBlock = instruction.parent
        traceBuilder += instruction
        thrownException = termException
    }

    override fun unary(
        value: String,
        operand: String,
        concreteValue: Any?,
        concreteOperand: Any?
    ) = safeCall {
        checkCall()

        val kfgValue = parseValue(value) as? UnaryInst
            ?: unreachable { log.error("Value does not correspond to trace") }
        val kfgOperand = parseValue(operand)

        val termValue = mkNewValue(kfgValue)
        val termOperand = mkValue(kfgOperand)

        terms[termValue] = kfgValue
        concreteValues[termValue] = concreteValue.getAsDescriptor(termValue.type)

        termOperand.updateInfo(kfgOperand, concreteOperand.getAsDescriptor(termOperand.type))

        val predicate = state(kfgValue.location) {
            termValue equality termOperand.apply(kfgValue.opcode)
        }
        predicates[predicate] = kfgValue
        builder += predicate

        traceBuilder += kfgValue
    }
}