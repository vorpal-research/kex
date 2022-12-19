package org.vorpal.research.kex.asm.transform

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.trace.symbolic.InstructionTraceCollector
import org.vorpal.research.kex.trace.symbolic.TraceCollectorProxy
import org.vorpal.research.kex.util.insertAfter
import org.vorpal.research.kex.util.insertBefore
import org.vorpal.research.kex.util.wrapValue
import org.vorpal.research.kfg.Package
import org.vorpal.research.kfg.arrayListClass
import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.*
import org.vorpal.research.kfg.ir.value.instruction.*
import org.vorpal.research.kfg.type.*
import org.vorpal.research.kfg.visitor.MethodVisitor

class SymbolicTraceInstrumenter(
    val executionContext: ExecutionContext,
    val ignores: Set<Package> = setOf()
) : MethodVisitor, InstructionBuilder {
    companion object {
        private val INSTRUCTION_TRACE_COLLECTOR = InstructionTraceCollector::class.java
            .canonicalName
            .replace(Package.CANONICAL_SEPARATOR, Package.SEPARATOR)

        private val TRACE_COLLECTOR_PROXY = TraceCollectorProxy::class.java
            .canonicalName
            .replace(Package.CANONICAL_SEPARATOR, Package.SEPARATOR)
    }

    override val ctx: UsageContext = EmptyUsageContext
    override val cm get() = executionContext.cm

    override val instructions: InstructionFactory
        get() = cm.instruction
    override val types: TypeFactory
        get() = cm.type
    override val values: ValueFactory
        get() = cm.value

    private val collectorClass = cm[INSTRUCTION_TRACE_COLLECTOR]
    private val collectorProxyClass = cm[TRACE_COLLECTOR_PROXY]
    private lateinit var traceCollector: Instruction

    override fun cleanup() {}

    private fun prepareStaticInitializer(method: Method) {
        val entryInstructions = buildList {
            traceCollector = getNewCollector()
            add(traceCollector)
            add(disableCollector())
        }
        val exitInstructions = setNewCollector(traceCollector)
        method.body.entry.first().insertBefore(entryInstructions)
        val returnInst = method.body.flatten().filterIsInstance<ReturnInst>().first()
        returnInst.insertBefore(exitInstructions)
    }

    override fun visit(method: Method) {
        if (!method.hasBody) return
        method.body.slotTracker.rerun()
        if (method.isStaticInitializer) {
            prepareStaticInitializer(method)
            return
        }
        if (ignores.any { it.isParent(method.klass.pkg) }) return

        val methodEntryInstructions: List<Instruction> = buildList {
            traceCollector = getNewCollector()
            add(traceCollector)
            val entryMethod = collectorClass.getMethod(
                "methodEnter", types.voidType,
                types.stringType, types.stringType, types.listType,
                types.stringType, types.objectType, types.listType
            )

            val arrayListKlass = cm.arrayListClass
            val initMethod = arrayListKlass.getMethod("<init>", types.voidType)
            val addMethod = arrayListKlass.getMethod("add", types.boolType, types.objectType)

            val argTypesList = types.arrayListType.new().also { add(it) }
            add(arrayListKlass.specialCall(initMethod, argTypesList))

            val argumentList = types.arrayListType.new().also { add(it) }
            add(arrayListKlass.specialCall(initMethod, argumentList))

            for ((index, arg) in method.argTypes.withIndex()) {
                add(
                    arrayListKlass.virtualCall(
                        addMethod, argTypesList, arg.asmDesc.asValue
                    )
                )
                val argument = values.getArgument(index, method, arg)
                add(
                    arrayListKlass.virtualCall(
                        addMethod, argumentList, argument.wrapped(this)
                    )
                )
            }
            val instance = when {
                method.isStatic || method.isConstructor -> values.nullConstant
                else -> values.getThis(method.klass)
            }

            add(
                collectorClass.interfaceCall(
                    entryMethod,
                    traceCollector,
                    method.klass.fullName.asValue,
                    method.name.asValue,
                    argTypesList,
                    method.returnType.asmDesc.asValue,
                    instance,
                    argumentList
                )
            )
        }
        super.visit(method)
        method.body.entry.first().insertBefore(methodEntryInstructions)
    }

    override fun visitArrayLoadInst(inst: ArrayLoadInst) {
        val arrayLoadMethod = collectorClass.getMethod(
            "arrayLoad", types.voidType,
            types.stringType, types.stringType, types.stringType,
            types.objectType, types.objectType, types.objectType
        )

        val before = buildList {
            addAll(addNullityConstraint(inst, inst.arrayRef))
            addAll(addArrayIndexConstraints(inst, inst.arrayRef, inst.index))
        }
        val after = buildList {
            add(
                collectorClass.interfaceCall(
                    arrayLoadMethod, traceCollector,
                    "$inst".asValue, "${inst.arrayRef}".asValue, "${inst.index}".asValue,
                    inst.wrapped(this), inst.arrayRef, inst.index.wrapped(this)
                )
            )
        }
        inst.insertBefore(before)
        inst.insertAfter(after)
    }

    override fun visitArrayStoreInst(inst: ArrayStoreInst) {
        val arrayStoreMethod = collectorClass.getMethod(
            "arrayStore", types.voidType,
            types.stringType, types.stringType, types.stringType, types.stringType,
            types.objectType, types.objectType, types.objectType
        )

        val before = buildList {
            addAll(addNullityConstraint(inst, inst.arrayRef))
            addAll(addArrayIndexConstraints(inst, inst.arrayRef, inst.index))
        }
        val after = buildList {
            add(
                collectorClass.interfaceCall(
                    arrayStoreMethod, traceCollector,
                    "$inst".asValue, "${inst.arrayRef}".asValue, "${inst.index}".asValue, "${inst.value}".asValue,
                    inst.arrayRef, inst.index.wrapped(this), inst.value.wrapped(this)
                )
            )
        }
        inst.insertBefore(before)
        inst.insertAfter(after)
    }

    override fun visitBinaryInst(inst: BinaryInst) {
        val binaryMethod = collectorClass.getMethod(
            "binary", types.voidType,
            types.stringType, types.stringType, types.stringType,
            types.objectType, types.objectType, types.objectType
        )

        val instrumented = buildList {
            add(
                collectorClass.interfaceCall(
                    binaryMethod, traceCollector,
                    "$inst".asValue, "${inst.lhv}".asValue, "${inst.rhv}".asValue,
                    inst.wrapped(this), inst.lhv.wrapped(this), inst.rhv.wrapped(this)
                )
            )
        }
        inst.insertAfter(instrumented)
    }

    override fun visitBranchInst(inst: BranchInst) {
        val branchMethod = collectorClass.getMethod(
            "branch", types.voidType,
            types.stringType, types.stringType
        )

        val instrumented = collectorClass.interfaceCall(
            branchMethod, traceCollector,
            "$inst".asValue, "${inst.cond}".asValue
        )
        inst.insertBefore(instrumented)
    }

    override fun visitCallInst(inst: CallInst) {
        val callMethod = collectorClass.getMethod(
            "call", types.voidType,
            types.stringType,
            types.stringType, types.stringType, types.listType, types.stringType,
            types.stringType, types.stringType, types.listType,
            types.listType
        )

        val calledMethod = inst.method
        val klass = calledMethod.klass

        val instrumented = buildList {
            if (!inst.isStatic && !inst.method.isConstructor) {
                addAll(addNullityConstraint(inst, inst.callee))
                addAll(addTypeConstraints(inst, inst.callee))
            }

            val arrayListKlass = cm.arrayListClass
            val initMethod = arrayListKlass.getMethod("<init>", types.voidType)
            val addMethod = arrayListKlass.getMethod("add", types.boolType, types.objectType)

            val argTypesList = types.arrayListType.new().also { add(it) }
            add(arrayListKlass.specialCall(initMethod, argTypesList))
            for (arg in calledMethod.argTypes) {
                add(
                    arrayListKlass.virtualCall(
                        addMethod, argTypesList, arg.asmDesc.asValue
                    )
                )
            }

            val argumentList = types.arrayListType.new().also { add(it) }
            add(arrayListKlass.specialCall(initMethod, argumentList))
            for (arg in inst.args) {
                add(
                    arrayListKlass.virtualCall(
                        addMethod, argumentList, "$arg".asValue
                    )
                )
            }

            val concreteArgumentsList = types.arrayListType.new().also { add(it) }
            add(arrayListKlass.specialCall(initMethod, concreteArgumentsList))
            for (arg in inst.args) {
                add(
                    arrayListKlass.virtualCall(
                        addMethod, concreteArgumentsList, arg.wrapped(this)
                    )
                )
            }

            val returnValue = when {
                inst.isNameDefined -> "$inst".asValue
                else -> values.nullConstant
            }
            val callee = when {
                inst.isStatic -> values.nullConstant
                else -> "${inst.callee}".asValue
            }

            add(
                collectorClass.interfaceCall(
                    callMethod,
                    traceCollector,
                    "$inst".asValue,
                    klass.fullName.asValue,
                    calledMethod.name.asValue,
                    argTypesList,
                    calledMethod.returnType.asmDesc.asValue,
                    returnValue,
                    callee,
                    argumentList,
                    concreteArgumentsList
                )
            )
        }
        inst.insertBefore(instrumented)
    }

    override fun visitCastInst(inst: CastInst) {
        val castMethod = collectorClass.getMethod(
            "cast", types.voidType,
            types.stringType, types.stringType,
            types.objectType, types.objectType
        )
        val before = buildList {
            if (inst.type.isReference) addAll(addNullityConstraint(inst, inst.operand))
            if (inst.type.isReference) addAll(addTypeConstraints(inst, inst.operand, inst.type))
        }
        val after = buildList {
            add(
                collectorClass.interfaceCall(
                    castMethod, traceCollector,
                    "$inst".asValue, "${inst.operand}".asValue,
                    inst.wrapped(this), inst.operand.wrapped(this)
                )
            )
        }
        inst.insertBefore(before)
        inst.insertAfter(after)
    }

    override fun visitCatchInst(inst: CatchInst) {
        val catchMethod = collectorClass.getMethod(
            "catch", types.voidType,
            types.stringType, types.objectType
        )

        val instrumented = collectorClass.interfaceCall(
            catchMethod, traceCollector,
            "$inst".asValue, inst
        )
        inst.insertAfter(instrumented)
    }

    override fun visitCmpInst(inst: CmpInst) {
        val cmpMethod = collectorClass.getMethod(
            "cmp", types.voidType,
            types.stringType, types.stringType, types.stringType,
            types.objectType, types.objectType
        )

        val instrumented = buildList {
            add(
                collectorClass.interfaceCall(
                    cmpMethod, traceCollector,
                    "$inst".asValue, "${inst.lhv}".asValue, "${inst.rhv}".asValue,
                    inst.lhv.wrapped(this), inst.rhv.wrapped(this)
                )
            )
        }
        inst.insertAfter(instrumented)
    }

    override fun visitEnterMonitorInst(inst: EnterMonitorInst) {
        val enterMonitorMethod = collectorClass.getMethod(
            "enterMonitor", types.voidType,
            types.stringType, types.stringType, types.objectType
        )
        val instrumented = collectorClass.interfaceCall(
            enterMonitorMethod, traceCollector,
            "$inst".asValue, "${inst.owner}".asValue, inst.owner
        )
        inst.insertAfter(instrumented)
    }

    override fun visitExitMonitorInst(inst: ExitMonitorInst) {
        val exitMonitorMethod = collectorClass.getMethod(
            "exitMonitor", types.voidType,
            types.stringType, types.stringType, types.objectType
        )
        val instrumented = collectorClass.interfaceCall(
            exitMonitorMethod, traceCollector,
            "$inst".asValue, "${inst.owner}".asValue, inst.owner
        )
        inst.insertAfter(instrumented)
    }

    override fun visitFieldLoadInst(inst: FieldLoadInst) {
        val fieldLoadMethod = collectorClass.getMethod(
            "fieldLoad", types.voidType,
            types.stringType, types.stringType, types.stringType,
            types.stringType, types.stringType,
            types.objectType, types.objectType
        )

        val before = when {
            !inst.isStatic -> addNullityConstraint(inst, inst.owner)
            else -> emptyList()
        }
        val fieldKlass = inst.field.klass.fullName.asValue
        val fieldName = inst.field.name.asValue
        val fieldType = inst.field.type.asmDesc.asValue
        val (owner, concreteOwner) = when {
            inst.isStatic -> values.nullConstant to values.nullConstant
            else -> "${inst.owner}".asValue to inst.owner
        }

        val after = buildList {
            add(
                collectorClass.interfaceCall(
                    fieldLoadMethod, traceCollector,
                    "$inst".asValue, owner, fieldKlass, fieldName, fieldType,
                    inst.wrapped(this), concreteOwner.wrapped(this)
                )
            )
        }
        inst.insertBefore(before)
        inst.insertAfter(after)
    }

    override fun visitFieldStoreInst(inst: FieldStoreInst) {
        val fieldStoreMethod = collectorClass.getMethod(
            "fieldStore", types.voidType,
            types.stringType,
            types.stringType, types.stringType, types.stringType,
            types.stringType, types.stringType,
            types.objectType, types.objectType
        )

        val before = when {
            !inst.isStatic -> addNullityConstraint(inst, inst.owner)
            else -> emptyList()
        }

        val fieldKlass = inst.field.klass.fullName.asValue
        val fieldName = inst.field.name.asValue
        val fieldType = inst.field.type.asmDesc.asValue
        val (owner, concreteOwner) = when {
            inst.isStatic -> values.nullConstant to values.nullConstant
            else -> "${inst.owner}".asValue to inst.owner
        }
        val defOwner = when {
            inst.hasOwner && inst.owner is ThisRef && inst.parent.method.isConstructor -> values.nullConstant
            else -> concreteOwner
        }

        val after = buildList {
            add(
                collectorClass.interfaceCall(
                    fieldStoreMethod, traceCollector,
                    "$inst".asValue,
                    owner, fieldKlass, fieldName, fieldType, "${inst.value}".asValue,
                    inst.value.wrapped(this), defOwner
                )
            )
        }
        inst.insertBefore(before)
        inst.insertAfter(after)
    }

    override fun visitInstanceOfInst(inst: InstanceOfInst) {
        val instanceOfMethod = collectorClass.getMethod(
            "instanceOf", types.voidType,
            types.stringType, types.stringType,
            types.objectType, types.objectType
        )

        val instrumented = buildList {
            add(
                collectorClass.interfaceCall(
                    instanceOfMethod, traceCollector,
                    "$inst".asValue, "${inst.operand}".asValue,
                    inst.wrapped(this), inst.operand.wrapped(this)
                )
            )
        }
        inst.insertAfter(instrumented)
    }

    override fun visitInvokeDynamicInst(inst: InvokeDynamicInst) {
        val invokeDynamicMethod = collectorClass.getMethod(
            "invokeDynamic", types.voidType,
            types.stringType, types.listType,
            types.objectType, types.listType
        )

        val instrumented = buildList {
            val arrayListKlass = cm.arrayListClass
            val initMethod = arrayListKlass.getMethod("<init>", types.voidType)
            val addMethod = arrayListKlass.getMethod("add", types.boolType, types.objectType)
            val args = types.arrayListType.new().also { add(it) }
            add(arrayListKlass.specialCall(initMethod, args))
            for (arg in inst.args) {
                add(
                    arrayListKlass.virtualCall(
                        addMethod, args, "$arg".asValue
                    )
                )
            }

            val concreteArgs = types.arrayListType.new().also { add(it) }
            add(arrayListKlass.specialCall(initMethod, concreteArgs))
            for (arg in inst.args) {
                add(
                    arrayListKlass.virtualCall(
                        addMethod, concreteArgs, arg.wrapped(this)
                    )
                )
            }

            add(
                collectorClass.interfaceCall(
                    invokeDynamicMethod, traceCollector,
                    "$inst".asValue, args,
                    inst.wrapped(this), concreteArgs
                )
            )
        }
        inst.insertAfter(instrumented)
    }

    override fun visitJumpInst(inst: JumpInst) {
        val jumpMethod = collectorClass.getMethod(
            "jump", types.voidType, types.stringType
        )

        val instrumented = collectorClass.interfaceCall(
            jumpMethod, traceCollector, "$inst".asValue
        )
        inst.insertBefore(instrumented)
    }

    override fun visitNewArrayInst(inst: NewArrayInst) {
        val newArrayMethod = collectorClass.getMethod(
            "newArray", types.voidType,
            types.stringType, types.listType,
            types.objectType, types.listType
        )

        val instrumented = buildList {
            val arrayListKlass = cm.arrayListClass
            val initMethod = arrayListKlass.getMethod("<init>", types.voidType)
            val addMethod = arrayListKlass.getMethod("add", types.boolType, types.objectType)
            val dimensions = types.arrayListType.new().also { add(it) }
            add(arrayListKlass.specialCall(initMethod, dimensions))
            for (dimension in inst.dimensions) {
                add(
                    arrayListKlass.virtualCall(
                        addMethod, dimensions, "$dimension".asValue
                    )
                )
            }

            val concreteDimensions = types.arrayListType.new().also { add(it) }
            add(arrayListKlass.specialCall(initMethod, concreteDimensions))
            for (dimension in inst.dimensions) {
                add(
                    arrayListKlass.virtualCall(
                        addMethod, concreteDimensions, dimension.wrapped(this)
                    )
                )
            }

            for (dimension in inst.dimensions) {
                addAll(addArrayLengthConstraints(inst, dimension))
            }

            add(
                collectorClass.interfaceCall(
                    newArrayMethod, traceCollector,
                    "$inst".asValue, dimensions,
                    inst, concreteDimensions
                )
            )
        }
        inst.insertAfter(instrumented)
    }

    override fun visitNewInst(inst: NewInst) {
        val newMethod = collectorClass.getMethod(
            "new", types.voidType, types.stringType
        )

        val instrumented = collectorClass.interfaceCall(
            newMethod, traceCollector, "$inst".asValue
        )
        inst.insertAfter(instrumented)
    }

    override fun visitPhiInst(inst: PhiInst) {
        val phiMethod = collectorClass.getMethod(
            "phi", types.voidType, types.stringType, types.objectType
        )

        val instrumented = buildList {
            add(
                collectorClass.interfaceCall(
                    phiMethod, traceCollector, "$inst".asValue, inst.wrapped(this)
                )
            )
        }
        inst.insertAfter(instrumented)
    }

    override fun visitReturnInst(inst: ReturnInst) {
        val returnMethod = collectorClass.getMethod(
            "ret", types.voidType,
            types.stringType, types.stringType, types.objectType
        )


        val (returnValue, concreteValue) = when {
            inst.hasReturnValue -> "${inst.returnValue}".asValue to inst.returnValue
            else -> values.nullConstant to values.nullConstant
        }
        val instrumented = buildList {
            add(
                collectorClass.interfaceCall(
                    returnMethod, traceCollector,
                    "$inst".asValue, returnValue, concreteValue.wrapped(this)
                )
            )
        }
        inst.insertBefore(instrumented)
    }

    override fun visitSwitchInst(inst: SwitchInst) {
        val switchMethod = collectorClass.getMethod(
            "switch", types.voidType,
            types.stringType, types.stringType, types.objectType
        )

        val instrumented = buildList {
            add(
                collectorClass.interfaceCall(
                    switchMethod, traceCollector,
                    "$inst".asValue, "${inst.key}".asValue, inst.key.wrapped(this)
                )
            )
        }
        inst.insertBefore(instrumented)
    }

    override fun visitTableSwitchInst(inst: TableSwitchInst) {
        val tableSwitchMethod = collectorClass.getMethod(
            "tableSwitch", types.voidType,
            types.stringType, types.stringType, types.objectType
        )

        val instrumented = buildList {
            add(
                collectorClass.interfaceCall(
                    tableSwitchMethod, traceCollector,
                    "$inst".asValue, "${inst.index}".asValue, inst.index.wrapped(this)
                )
            )
        }
        inst.insertBefore(instrumented)
    }

    override fun visitThrowInst(inst: ThrowInst) {
        val throwMethod = collectorClass.getMethod(
            "throwing", types.voidType,
            types.stringType, types.stringType, types.objectType
        )

        val instrumented = collectorClass.interfaceCall(
            throwMethod, traceCollector,
            "$inst".asValue, "${inst.throwable}".asValue, inst.throwable
        )
        inst.insertBefore(instrumented)
    }

    override fun visitUnaryInst(inst: UnaryInst) {
        val unaryMethod = collectorClass.getMethod(
            "unary", types.voidType,
            types.stringType, types.stringType,
            types.objectType, types.objectType
        )

        val before = when (inst.opcode) {
            UnaryOpcode.LENGTH -> addNullityConstraint(inst, inst.operand)
            else -> emptyList()
        }
        val after = buildList {
            add(
                collectorClass.interfaceCall(
                    unaryMethod, traceCollector,
                    "$inst".asValue, "${inst.operand}".asValue,
                    inst.wrapped(this), inst.operand.wrapped(this)
                )
            )
        }
        inst.insertBefore(before)
        inst.insertAfter(after)
    }

    private fun addNullityConstraint(inst: Instruction, value: Value): List<Instruction> = buildList {
        if (inst.parent.method.isConstructor && value is ThisRef) return@buildList

        val addNullityConstraintsMethod = collectorClass.getMethod(
            "addNullityConstraints", types.voidType,
            types.stringType, types.stringType, types.objectType
        )

        add(
            collectorClass.interfaceCall(
                addNullityConstraintsMethod, traceCollector,
                "$inst".asValue, "$value".asValue,
                value.wrapped(this)
            )
        )
    }

    private fun addTypeConstraints(inst: Instruction, value: Value): List<Instruction> = buildList {
        val addTypeConstraintsMethod = collectorClass.getMethod(
            "addTypeConstraints", types.voidType,
            types.stringType, types.stringType, types.objectType
        )

        add(
            collectorClass.interfaceCall(
                addTypeConstraintsMethod, traceCollector,
                "$inst".asValue, "$value".asValue,
                value.wrapped(this)
            )
        )
    }

    private fun addTypeConstraints(inst: Instruction, value: Value, type: Type): List<Instruction> = buildList {
        val addTypeConstraintsMethod = collectorClass.getMethod(
            "addTypeConstraints", types.voidType,
            types.stringType, types.stringType, types.stringType, types.objectType
        )

        add(
            collectorClass.interfaceCall(
                addTypeConstraintsMethod, traceCollector,
                "$inst".asValue, "$value".asValue, type.name.asValue, value.wrapped(this)
            )
        )
    }

    private fun addArrayIndexConstraints(inst: Instruction, array: Value, index: Value): List<Instruction> = buildList {
        val addArrayIndexConstraintsMethod = collectorClass.getMethod(
            "addArrayIndexConstraints", types.voidType,
            types.stringType,
            types.stringType, types.stringType,
            types.objectType, types.objectType
        )

        add(
            collectorClass.interfaceCall(
                addArrayIndexConstraintsMethod, traceCollector,
                "$inst".asValue,
                "$array".asValue, "$index".asValue,
                array.wrapped(this), index.wrapped(this)
            )
        )
    }

    private fun addArrayLengthConstraints(inst: Instruction, length: Value): List<Instruction> = buildList {
        val addArrayIndexConstraintsMethod = collectorClass.getMethod(
            "addArrayLengthConstraints", types.voidType,
            types.stringType,
            types.stringType, types.objectType
        )

        add(
            collectorClass.interfaceCall(
                addArrayIndexConstraintsMethod, traceCollector,
                "$inst".asValue,
                "$length".asValue, length.wrapped(this)
            )
        )
    }

    private fun getNewCollector(): Instruction {
        val getter = collectorProxyClass.getMethod("currentCollector", cm.type.getRefType(collectorClass))

        return getter.staticCall(collectorProxyClass, "collector", arrayOf())
    }

    private fun setNewCollector(collector: Value): Instruction {
        val setter =
            collectorProxyClass.getMethod("setCurrentCollector", cm.type.voidType, cm.type.getRefType(collectorClass))

        return setter.staticCall(collectorProxyClass, arrayOf(collector))
    }

    private fun disableCollector(): Instruction {
        val disabler = collectorProxyClass.getMethod("disableCollector", cm.type.voidType)

        return disabler.staticCall(collectorProxyClass, arrayOf())
    }

    private fun Class.virtualCall(
        method: Method,
        instance: Value,
        vararg args: Value
    ) = method.virtualCall(this, instance, args.toList().toTypedArray())

    private fun Class.interfaceCall(
        method: Method,
        instance: Value,
        vararg args: Value
    ) = method.interfaceCall(this, instance, args.toList().toTypedArray())

    private fun Class.specialCall(
        method: Method,
        instance: Value,
        vararg args: Value
    ) = method.specialCall(this, instance, args.toList().toTypedArray())

    private fun Value.wrapped(list: MutableList<Instruction>): Value = when {
        this.type.isPrimitive -> wrapValue(this).also {
            list += it
        }

        else -> this
    }
}