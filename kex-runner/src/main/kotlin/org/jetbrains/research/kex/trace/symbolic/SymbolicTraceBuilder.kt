@file:Suppress("unused", "DuplicatedCode")

package org.jetbrains.research.kex.trace.symbolic

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.ktype.kexType
import org.jetbrains.research.kex.reanimator.descriptor.Descriptor
import org.jetbrains.research.kex.reanimator.descriptor.Object2DescriptorConverter
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kex.state.StateBuilder
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.predicate.path
import org.jetbrains.research.kex.state.predicate.state
import org.jetbrains.research.kex.state.term.Term
import org.jetbrains.research.kex.state.term.term
import org.jetbrains.research.kex.trace.file.UnknownNameException
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
import org.jetbrains.research.kthelper.assert.ktassert
import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.collection.stackOf
import org.jetbrains.research.kthelper.logging.log

class SymbolicPathCondition : PathCondition {
    override val path = arrayListOf<Clause>()

    operator fun plusAssign(clause: Clause) {
        path += clause
    }
}

class SymbolicTraceBuilder(val ctx: ExecutionContext) : SymbolicState, InstructionTraceCollector {
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

    override val trace: List<Instruction>
        get() = traceBuilder

    private val cm get() = ctx.cm
    private val converter = Object2DescriptorConverter()
    private val builder = StateBuilder()
    private val traceBuilder = arrayListOf<Instruction>()
    private val pathBuilder = SymbolicPathCondition()
    private val concreteValues = mutableMapOf<Term, Descriptor>()
    private val terms = mutableMapOf<Term, Value>()
    private val predicates = mutableMapOf<Predicate, Instruction>()
    private val methodStack = stackOf<Method>()
    private val currentMethod: Method
        get() = methodStack.peek()
    private var counter = 0
    private val frameMap = stackOf<MutableMap<Value, Term>>(mutableMapOf())
    private val valueMap get() = frameMap.peek()
    private val returnReceivers = stackOf<Term?>()
    private var lastCall: Call? = null
    private lateinit var previousBlock: BasicBlock

    data class Call(
        val call: CallInst,
        val method: Method,
        val receiver: Value?,
        val params: Map<Value, Term>,
        val predicate: Predicate
    )

    private fun String.toType() = parseDesc(cm.type, this)

    private fun parseMethod(className: String, methodName: String, args: Array<String>, retType: String): Method {
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
            valueName.matches(Regex("\\d+")) -> cm.value.getIntConstant(valueName.toInt())
            valueName.matches(Regex("\\d+.\\d+")) -> cm.value.getDoubleConstant(valueName.toDouble())
            valueName.matches(Regex("-\\d+")) -> cm.value.getIntConstant(valueName.toInt())
            valueName.matches(Regex("-\\d+.\\d+")) -> cm.value.getDoubleConstant(valueName.toDouble())
            valueName.matches(Regex("\".*\"")) -> cm.value.getStringConstant(
                valueName.substring(
                    1,
                    valueName.lastIndex
                )
            )
            valueName.matches(Regex("\"[\n\\s]*\"")) -> cm.value.getStringConstant(
                valueName.substring(
                    1,
                    valueName.lastIndex
                )
            )
            valueName.matches(Regex(".*(/.*)+.class")) -> cm.value.getClassConstant("L${valueName.removeSuffix(".class")};")
            valueName == "null" -> cm.value.getNullConstant()
            else -> throw UnknownNameException(valueName)
        }
    }

    private fun newValue(value: Value) = term {
        when (value) {
            is Argument -> arg(value)
            is Constant -> const(value)
            is ThisRef -> `this`(value.type.kexType)
            else -> tf.getValue(value.type.kexType, "$value.${++counter}")
        }
    }

    private fun mkValue(value: Value) = valueMap.getOrPut(value) { newValue(value) }

    private fun mkNewValue(value: Value): Term {
        val v = newValue(value)
        valueMap[value] = v
        return v
    }

    private val Any?.descriptor get() = with(converter) { this@with.descriptor }

    infix fun Method.overrides(other: Method): Boolean = when {
        this == other -> true
        other.isFinal -> false
        this.`class` !is ConcreteClass -> false
        other.`class` !is ConcreteClass -> false
        this.name != other.name -> false
        this.desc != other.desc -> false
        !this.`class`.isInheritorOf(other.`class`) -> false
        else -> true
    }

    override fun methodEnter(
        className: String,
        methodName: String,
        argTypes: Array<String>,
        retType: String,
        instance: Any?,
        args: Array<Any?>
    ) {
        val method = parseMethod(className, methodName, argTypes, retType)
        if (lastCall != null) {
            val call = lastCall!!
            ktassert(call.method overrides method)

            call.receiver?.run { returnReceivers.push(mkNewValue(this)) }
            frameMap += mutableMapOf()
            for ((arg, value) in call.params) {
                valueMap[arg] = value
            }
            lastCall = null
        }
        methodStack.push(method)
    }

    private fun checkCall() {
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
    ) {
        checkCall()

        val kfgValue = parseValue(value) as? ArrayLoadInst
            ?: unreachable { log.error("Value does not correspond to trace") }
        val kfgRef = parseValue(arrayRef)
        val kfgIndex = parseValue(index)

        val termValue = mkNewValue(kfgValue)
        val termRef = mkValue(kfgRef)
        val termIndex = mkValue(kfgIndex)
        terms[termValue] = kfgValue
        terms[termRef] = kfgRef
        terms[termIndex] = kfgIndex

        concreteValues[termValue] = concreteValue.descriptor
        concreteValues[termRef] = concreteRef.descriptor
        concreteValues[termIndex] = concreteIndex.descriptor

        val predicate = state(kfgValue.location) {
            termValue equality termRef[termIndex].load()
        }
        predicates[predicate] = kfgValue
        builder += predicate

        traceBuilder += kfgValue
    }

    override fun arrayStore(
        arrayRef: String,
        index: String,
        value: String,
        concreteRef: Any?,
        concreteIndex: Any?,
        concreteValue: Any?
    ) {
        checkCall()

        val kfgRef = parseValue(arrayRef)
        val kfgIndex = parseValue(index)
        val kfgValue = parseValue(value)

        val termRef = mkValue(kfgRef)
        val termIndex = mkValue(kfgIndex)
        val termValue = mkValue(kfgValue)
        terms[termRef] = kfgRef
        terms[termIndex] = kfgIndex
        terms[termValue] = kfgValue

        concreteValues[termRef] = concreteRef.descriptor
        concreteValues[termIndex] = concreteIndex.descriptor
        concreteValues[termValue] = concreteValue.descriptor

        val inst = currentMethod
            .flatten()
            .filterIsInstance<ArrayStoreInst>()
            .firstOrNull { it.arrayRef == kfgRef && it.index == kfgIndex && it.value == kfgValue }
            ?: unreachable { log.error("Value does not correspond to trace") }
        val predicate = state(inst.location) {
            termRef[termIndex].store(termValue)
        }
        predicates[predicate] = inst
        builder += predicate

        traceBuilder += inst
    }

    override fun binary(
        value: String,
        lhv: String,
        rhv: String,
        concreteValue: Any?,
        concreteLhv: Any?,
        concreteRhv: Any?
    ) {
        checkCall()

        val kfgValue = parseValue(value) as? BinaryInst
            ?: unreachable { log.error("Value does not correspond to trace") }
        val kfgLhv = parseValue(lhv)
        val kfgRhv = parseValue(rhv)

        val termValue = mkNewValue(kfgValue)
        val termLhv = mkValue(kfgLhv)
        val termRhv = mkValue(kfgRhv)
        terms[termValue] = kfgValue
        terms[termLhv] = kfgLhv
        terms[termRhv] = kfgRhv

        concreteValues[termValue] = concreteValue.descriptor
        concreteValues[termLhv] = concreteLhv.descriptor
        concreteValues[termRhv] = concreteRhv.descriptor

        val predicate = state(kfgValue.location) {
            termValue equality termLhv.apply(cm.type, kfgValue.opcode, termRhv)
        }
        predicates[predicate] = kfgValue
        builder += predicate

        traceBuilder += kfgValue
    }

    override fun branch(
        condition: String,
        currentBlock: String,
        concreteCondition: Any?
    ) {
        checkCall()

        val current = parseBlock(currentBlock)

        val kfgCondition = parseValue(condition)
        val inst = current.terminator as BranchInst

        val termCondition = mkValue(kfgCondition)
        terms[termCondition] = kfgCondition

        val booleanValue = concreteCondition as Boolean
        concreteValues[termCondition] = booleanValue.descriptor

        val predicate = path(inst.location) {
            termCondition equality booleanValue
        }
        predicates[predicate] = inst
        builder += predicate

        previousBlock = inst.parent

        traceBuilder += inst
    }

    override fun call(
        className: String,
        methodName: String,
        argTypes: Array<String>,
        retType: String,
        returnValue: String?,
        callee: String?,
        arguments: List<String>,
        concreteReturn: Any?,
        concreteCallee: Any?,
        concreteArguments: List<Any?>
    ) {
        checkCall()

        val calledMethod = parseMethod(className, methodName, argTypes, retType)
        val kfgReturn = returnValue?.let { parseValue(it) }
        val kfgCallee = callee?.let { parseValue(it) }
        val kfgArguments = arguments.map { parseValue(it) }
        val inst = currentMethod
            .flatten()
            .filterIsInstance<CallInst>()
            .firstOrNull {
                it.method == calledMethod && it.args == kfgArguments &&
                        it.isStatic == (callee == null) && it.hasRealName == (returnValue == null)
            }
            ?: unreachable { log.error("Value does not correspond to trace") }

        val termReturn = kfgReturn?.let { mkNewValue(it) }
        val termCallee = kfgCallee?.let { mkValue(it) }
        val termArguments = kfgArguments.map { mkValue(it) }
        termReturn?.apply { terms[this] = kfgReturn }
        termCallee?.apply { terms[this] = kfgCallee }
        for ((tArg, kArg) in termArguments.zip(kfgArguments)) {
            terms[tArg] = kArg
        }

        termReturn?.apply { concreteValues[this] = concreteReturn.descriptor }
        termCallee?.apply { concreteValues[this] = concreteCallee.descriptor }
        for ((arg, concrete) in termArguments.zip(concreteArguments)) {
            concreteValues[arg] = concrete.descriptor
        }

        val predicate = state(inst.location) {
            val actualCallee = termCallee ?: `class`(calledMethod.`class`)
            when {
                termReturn != null -> termReturn equality actualCallee.call(calledMethod, termArguments)
                else -> call(actualCallee.call(calledMethod, termArguments))
            }
        }
//        predicates[predicate] = inst
//        builder += predicate

        lastCall = Call(
            inst,
            calledMethod,
            kfgReturn,
            (kfgArguments.zip(termArguments) + listOfNotNull(
                kfgReturn?.let { it to termReturn!! }
            )).toMap(),
            predicate
        )

        traceBuilder += inst
    }

    override fun cast(
        value: String,
        operand: String,
        concreteValue: Any?,
        concreteOperand: Any?
    ) {
        checkCall()

        val kfgValue = parseValue(value) as? CastInst
            ?: unreachable { log.error("Value does not correspond to trace") }
        val kfgOperand = parseValue(operand)

        val termValue = mkNewValue(kfgValue)
        val termOperand = mkValue(kfgOperand)
        terms[termValue] = kfgValue
        terms[termOperand] = kfgOperand
        concreteValues[termValue] = concreteValue.descriptor
        concreteValues[termOperand] = concreteOperand.descriptor

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
    ) {
        checkCall()

        // todo
    }

    override fun cmp(
        value: String,
        lhv: String,
        rhv: String,
        concreteValue: Any?,
        concreteLhv: Any?,
        concreteRhv: Any?
    ) {
        checkCall()

        val kfgValue = parseValue(value) as? CmpInst
            ?: unreachable { log.error("Value does not correspond to trace") }
        val kfgLhv = parseValue(lhv)
        val kfgRhv = parseValue(rhv)

        val termValue = mkNewValue(kfgValue)
        val termLhv = mkValue(kfgLhv)
        val termRhv = mkValue(kfgRhv)
        terms[termValue] = kfgValue
        terms[termLhv] = kfgLhv
        terms[termRhv] = kfgRhv

        concreteValues[termValue] = concreteValue.descriptor
        concreteValues[termLhv] = concreteLhv.descriptor
        concreteValues[termRhv] = concreteRhv.descriptor

        val predicate = state(kfgValue.location) {
            termValue equality termLhv.apply(kfgValue.opcode, termRhv)
        }
        predicates[predicate] = kfgValue
        builder += predicate

        traceBuilder += kfgValue
    }

    override fun enterMonitor(
        operand: String,
        concreteOperand: Any?
    ) {
        checkCall()

        // todo
    }

    override fun exitMonitor(
        operand: String,
        concreteOperand: Any?
    ) {
        checkCall()

        // todo
    }

    override fun fieldLoad(
        value: String,
        owner: String?,
        klass: String,
        field: String,
        type: String,
        concreteValue: Any?,
        concreteOwner: Any?
    ) {
        checkCall()

        val kfgValue = parseValue(value) as? FieldLoadInst
            ?: unreachable { log.error("Value does not correspond to trace") }
        val kfgOwner = owner?.let { parseValue(it) }
        val kfgField = cm[klass].getField(field, parseDesc(cm.type, type))

        val termValue = mkNewValue(kfgValue)
        val termOwner = kfgOwner?.let { mkValue(it) }
        terms[termValue] = kfgValue
        termOwner?.apply { terms[this] = kfgOwner }

        concreteValues[termValue] = concreteValue.descriptor
        termOwner?.apply { concreteValues[this] = concreteOwner.descriptor }

        val predicate = state(kfgValue.location) {
            val actualOwner = termOwner ?: `class`(kfgField.`class`)
            termValue equality actualOwner.field(kfgField.type.kexType, kfgField.name)
        }
        predicates[predicate] = kfgValue
        builder += predicate

        traceBuilder += kfgValue
    }

    override fun fieldStore(
        owner: String?,
        klass: String,
        field: String,
        type: String,
        value: String,
        concreteValue: Any?,
        concreteOwner: Any?
    ) {
        checkCall()

        val kfgOwner = owner?.let { parseValue(it) }
        val kfgField = cm[klass].getField(field, parseDesc(cm.type, type))
        val kfgValue = parseValue(value)
        val inst = currentMethod
            .flatten()
            .filterIsInstance<FieldStoreInst>()
            .firstOrNull { it.field == kfgField && it.hasOwner == (kfgOwner != null) && it.value == kfgValue }
            ?: unreachable { log.error("Value does not correspond to trace") }

        val termOwner = kfgOwner?.let { mkValue(it) }
        val termValue = mkValue(kfgValue)
        termOwner?.apply { terms[this] = kfgOwner }
        terms[termValue] = kfgValue

        termOwner?.apply { concreteValues[this] = concreteOwner.descriptor }
        concreteValues[termValue] = concreteValue.descriptor

        val predicate = state(inst.location) {
            val actualOwner = termOwner ?: `class`(kfgField.`class`)
            actualOwner.field(kfgField.type.kexType, kfgField.name).store(termValue)
        }
        predicates[predicate] = inst
        builder += predicate

        traceBuilder += inst
    }

    override fun instanceOf(
        value: String,
        operand: String,
        concreteValue: Any?,
        concreteOperand: Any?
    ) {
        checkCall()

        val kfgValue = parseValue(value) as? InstanceOfInst
            ?: unreachable { log.error("Value does not correspond to trace") }
        val kfgOperand = parseValue(operand)

        val termValue = mkNewValue(kfgValue)
        val termOperand = mkValue(kfgOperand)
        concreteValues[termValue] = concreteValue.descriptor
        concreteValues[termOperand] = concreteOperand.descriptor

        val predicate = state(kfgValue.location) {
            termValue equality (termOperand `is` kfgValue.targetType.kexType)
        }
        predicates[predicate] = kfgValue
        builder += predicate

        traceBuilder += kfgValue
    }

    override fun jump(
        currentBlock: String,
        targetBlock: String
    ) {
        checkCall()

        val current = parseBlock(currentBlock)
//        val target = parseBlock(targetBlock)

        previousBlock = current

        traceBuilder += current.terminator
    }

    override fun newArray(
        value: String,
        dimensions: List<String>,
        concreteValue: Any?,
        concreteDimensions: List<Any?>
    ) {
        checkCall()

        val kfgValue = parseValue(value) as? NewArrayInst
            ?: unreachable { log.error("Value does not correspond to trace") }
        val kfgDimensions = dimensions.map { parseValue(it) }

        val termValue = mkNewValue(kfgValue)
        val termDimensions = kfgDimensions.map { mkValue(it) }
        terms[termValue] = kfgValue
        for ((term, kfg) in termDimensions.zip(kfgDimensions)) {
            terms[term] = kfg
        }

        concreteValues[termValue] = concreteValue.descriptor
        for ((term, any) in termDimensions.zip(concreteDimensions)) {
            concreteValues[term] = any.descriptor
        }

        val predicate = state(kfgValue.location) {
            termValue.new(termDimensions)
        }
        predicates[predicate] = kfgValue
        builder += predicate

        traceBuilder += kfgValue
    }

    override fun new(
        value: String,
        concreteValue: Any?
    ) {
        checkCall()

        val kfgValue = parseValue(value) as? NewInst
            ?: unreachable { log.error("Value does not correspond to trace") }

        val termValue = mkNewValue(kfgValue)
        terms[termValue] = kfgValue

        concreteValues[termValue] = concreteValue.descriptor

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
    ) {
        checkCall()

        val kfgValue = parseValue(value) as? PhiInst
            ?: unreachable { log.error("Value does not correspond to trace") }
        val kfgIncoming = kfgValue.incomings[previousBlock]!!

        val termValue = mkNewValue(kfgValue)
        val termIncoming = mkValue(kfgIncoming)
        terms[termValue] = kfgValue
        terms[termIncoming] = kfgIncoming

        concreteValues[termValue] = concreteValue.descriptor

        val predicate = state(kfgValue.location) {
            termValue equality termIncoming
        }
        predicates[predicate] = kfgValue
        builder += predicate

        traceBuilder += kfgValue
    }

    override fun ret(
        returnValue: String?,
        currentBlock: String,
        concreteValue: Any?
    ) {
        checkCall()

        val current = parseBlock(currentBlock)
        val inst = current.terminator as ReturnInst
        val kfgReturn = returnValue?.let { parseValue(it) }

        val termReturn = kfgReturn?.let { mkValue(it) }

        if (termReturn != null) {
            val receiver = returnReceivers.pop()!!
            val predicate = state(inst.location) {
                receiver equality termReturn
            }

            predicates[predicate] = inst
            builder += predicate
        }

        traceBuilder += current.terminator
    }

    override fun switch(
        value: String,
        currentBlock: String,
        concreteValue: Any?
    ) {
        checkCall()

        val current = parseBlock(currentBlock)
        val inst = current.terminator as SwitchInst

        val kfgValue = parseValue(value)
        val termValue = mkValue(kfgValue)
        terms[termValue] = kfgValue

        val intValue = concreteValue as Int
        concreteValues[termValue] = intValue.descriptor

        val predicate = path(inst.location) {
            termValue equality intValue
        }
        predicates[predicate] = inst
        builder += predicate

        previousBlock = current

        traceBuilder += inst
    }

    override fun tableSwitch(
        value: String,
        currentBlock: String,
        concreteValue: Any?
    ) {
        checkCall()

        val current = parseBlock(currentBlock)
        val inst = current.terminator as TableSwitchInst

        val kfgValue = parseValue(value)
        val termValue = mkValue(kfgValue)
        terms[termValue] = kfgValue

        val intValue = concreteValue as Int
        concreteValues[termValue] = intValue.descriptor

        val predicate = path(inst.location) {
            termValue equality intValue
        }
        predicates[predicate] = inst
        builder += predicate

        previousBlock = current

        traceBuilder += inst
    }

    override fun throwing(
        currentBlock: String,
        exception: String,
        concreteException: Any?
    ) {
        checkCall()

        val current = parseBlock(currentBlock)
        val inst = current.terminator as ThrowInst

        val kfgException = parseValue(exception)
        val termException = mkValue(kfgException)
        terms[termException] = kfgException
        concreteValues[termException] = concreteException.descriptor

        val predicate = path(inst.location) {
            `throw`(termException)
        }
        predicates[predicate] = inst
        builder += predicate
        previousBlock = current

        traceBuilder += inst
    }

    override fun unary(
        value: String,
        operand: String,
        concreteValue: Any?,
        concreteOperand: Any?
    ) {
        checkCall()

        val kfgValue = parseValue(value) as? UnaryInst
            ?: unreachable { log.error("Value does not correspond to trace") }
        val kfgOperand = parseValue(operand)

        val termValue = mkNewValue(kfgValue)
        val termOperand = mkValue(kfgOperand)
        terms[termValue] = kfgValue
        terms[termOperand] = kfgOperand

        concreteValues[termValue] = concreteValue.descriptor
        concreteValues[termOperand] = concreteOperand.descriptor

        val predicate = state(kfgValue.location) {
            termValue equality termOperand.apply(kfgValue.opcode)
        }
        predicates[predicate] = kfgValue
        builder += predicate

        traceBuilder += kfgValue
    }
}