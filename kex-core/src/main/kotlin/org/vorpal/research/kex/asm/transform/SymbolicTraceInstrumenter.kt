package org.vorpal.research.kex.asm.transform

import org.vorpal.research.kex.trace.symbolic.InstructionTraceCollector
import org.vorpal.research.kex.trace.symbolic.TraceCollectorProxy
import org.vorpal.research.kex.util.asmString
import org.vorpal.research.kex.util.insertAfter
import org.vorpal.research.kex.util.insertBefore
import org.vorpal.research.kex.util.wrapUpValue
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.Package
import org.vorpal.research.kfg.arrayListClass
import org.vorpal.research.kfg.ir.CatchBlock
import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kfg.ir.Location
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.EmptyUsageContext
import org.vorpal.research.kfg.ir.value.ThisRef
import org.vorpal.research.kfg.ir.value.UsageContext
import org.vorpal.research.kfg.ir.value.Value
import org.vorpal.research.kfg.ir.value.ValueFactory
import org.vorpal.research.kfg.ir.value.instruction.ArrayLoadInst
import org.vorpal.research.kfg.ir.value.instruction.ArrayStoreInst
import org.vorpal.research.kfg.ir.value.instruction.BinaryInst
import org.vorpal.research.kfg.ir.value.instruction.BranchInst
import org.vorpal.research.kfg.ir.value.instruction.CallInst
import org.vorpal.research.kfg.ir.value.instruction.CallOpcode
import org.vorpal.research.kfg.ir.value.instruction.CastInst
import org.vorpal.research.kfg.ir.value.instruction.CatchInst
import org.vorpal.research.kfg.ir.value.instruction.CmpInst
import org.vorpal.research.kfg.ir.value.instruction.EnterMonitorInst
import org.vorpal.research.kfg.ir.value.instruction.ExitMonitorInst
import org.vorpal.research.kfg.ir.value.instruction.FieldLoadInst
import org.vorpal.research.kfg.ir.value.instruction.FieldStoreInst
import org.vorpal.research.kfg.ir.value.instruction.InstanceOfInst
import org.vorpal.research.kfg.ir.value.instruction.Instruction
import org.vorpal.research.kfg.ir.value.instruction.InstructionBuilder
import org.vorpal.research.kfg.ir.value.instruction.InstructionFactory
import org.vorpal.research.kfg.ir.value.instruction.InvokeDynamicInst
import org.vorpal.research.kfg.ir.value.instruction.JumpInst
import org.vorpal.research.kfg.ir.value.instruction.NewArrayInst
import org.vorpal.research.kfg.ir.value.instruction.NewInst
import org.vorpal.research.kfg.ir.value.instruction.PhiInst
import org.vorpal.research.kfg.ir.value.instruction.ReturnInst
import org.vorpal.research.kfg.ir.value.instruction.SwitchInst
import org.vorpal.research.kfg.ir.value.instruction.TableSwitchInst
import org.vorpal.research.kfg.ir.value.instruction.ThrowInst
import org.vorpal.research.kfg.ir.value.instruction.UnaryInst
import org.vorpal.research.kfg.ir.value.instruction.UnaryOpcode
import org.vorpal.research.kfg.type.Type
import org.vorpal.research.kfg.type.TypeFactory
import org.vorpal.research.kfg.type.arrayListType
import org.vorpal.research.kfg.type.listType
import org.vorpal.research.kfg.type.objectType
import org.vorpal.research.kfg.type.stringType
import org.vorpal.research.kfg.visitor.MethodVisitor

class SymbolicTraceInstrumenter(
    override val cm: ClassManager,
    private val ignores: Set<Package> = setOf()
) : MethodVisitor, InstructionBuilder {
    companion object {
        val SYMBOLIC_TRACE_LOCATION = Location(
            pkg = Package.parse("org.vorpal.research.kex.asm.transform"),
            file = "SymbolicTraceInstrumenter",
            line = 62
        )

        private val INSTRUCTION_TRACE_COLLECTOR = InstructionTraceCollector::class.java
            .canonicalName
            .asmString

        private val TRACE_COLLECTOR_PROXY = TraceCollectorProxy::class.java
            .canonicalName
            .asmString
    }

    override val ctx: UsageContext = EmptyUsageContext
    override val instructions: InstructionFactory
        get() = cm.instruction
    override val types: TypeFactory
        get() = cm.type
    override val values: ValueFactory
        get() = cm.value

    private val collectorClass = cm[INSTRUCTION_TRACE_COLLECTOR]
    private val collectorProxyClass = cm[TRACE_COLLECTOR_PROXY]
    private val objectType = types.objectType
    private val stringType = types.stringType
    private val listType = types.listType
    private val arrayListType = types.arrayListType
    private lateinit var traceCollector: Instruction

    override fun cleanup() {}

    private fun Instruction.mapLocation(): Instruction {
        this.withLocation(SYMBOLIC_TRACE_LOCATION)
        return this
    }

    private fun List<Instruction>.mapLocation(): List<Instruction> {
        for (inst in this) {
            inst.withLocation(SYMBOLIC_TRACE_LOCATION)
        }
        return this
    }

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
                stringType, stringType, listType,
                stringType, objectType, listType
            )

            val arrayListKlass = cm.arrayListClass
            val initMethod = arrayListKlass.getMethod("<init>", types.voidType)
            val addMethod = arrayListKlass.getMethod("add", types.boolType, objectType)

            val argTypesList = arrayListType.new().also { add(it) }
            add(arrayListKlass.specialCall(initMethod, argTypesList, emptyList()))

            val argumentList = arrayListType.new().also { add(it) }
            add(arrayListKlass.specialCall(initMethod, argumentList, emptyList()))

            for ((index, arg) in method.argTypes.withIndex()) {
                add(
                    arrayListKlass.virtualCall(
                        addMethod, argTypesList, listOf(arg.asmDesc.asValue)
                    )
                )
                val argument = values.getArgument(index, method, arg)
                add(
                    arrayListKlass.virtualCall(
                        addMethod, argumentList, listOf(argument.wrapped(this))
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
                    listOf(
                        method.klass.fullName.asValue,
                        method.name.asValue,
                        argTypesList,
                        method.returnType.asmDesc.asValue,
                        instance,
                        argumentList
                    )
                )
            )
        }
        super.visit(method)
        method.body.entry.first().insertBefore(methodEntryInstructions.mapLocation())
    }

    override fun visitArrayLoadInst(inst: ArrayLoadInst) {
        val arrayLoadMethod = collectorClass.getMethod(
            "arrayLoad", types.voidType,
            stringType, stringType, stringType,
            objectType, objectType, objectType
        )

        val before = buildList {
            addAll(addNullityConstraint(inst, inst.arrayRef))
            addAll(addArrayIndexConstraints(inst, inst.arrayRef, inst.index))
        }
        val after = buildList {
            add(
                collectorClass.interfaceCall(
                    arrayLoadMethod, traceCollector,
                    listOf(
                        "$inst".asValue,
                        "${inst.arrayRef}".asValue,
                        "${inst.index}".asValue,
                        inst.wrapped(this),
                        inst.arrayRef,
                        inst.index.wrapped(this)
                    )
                )
            )
        }
        inst.insertBefore(before.mapLocation())
        inst.insertAfter(after.mapLocation())
    }

    override fun visitArrayStoreInst(inst: ArrayStoreInst) {
        val arrayStoreMethod = collectorClass.getMethod(
            "arrayStore", types.voidType,
            stringType, stringType, stringType, stringType,
            objectType, objectType, objectType
        )

        val before = buildList {
            addAll(addNullityConstraint(inst, inst.arrayRef))
            addAll(addArrayIndexConstraints(inst, inst.arrayRef, inst.index))
        }
        val after = buildList {
            add(
                collectorClass.interfaceCall(
                    arrayStoreMethod, traceCollector,
                    listOf(
                        "$inst".asValue,
                        "${inst.arrayRef}".asValue,
                        "${inst.index}".asValue,
                        "${inst.value}".asValue,
                        inst.arrayRef,
                        inst.index.wrapped(this),
                        inst.value.wrapped(this)
                    )
                )
            )
        }
        inst.insertBefore(before.mapLocation())
        inst.insertAfter(after.mapLocation())
    }

    override fun visitBinaryInst(inst: BinaryInst) {
        val binaryMethod = collectorClass.getMethod(
            "binary", types.voidType,
            stringType, stringType, stringType,
            objectType, objectType, objectType
        )

        val instrumented = buildList {
            add(
                collectorClass.interfaceCall(
                    binaryMethod, traceCollector,
                    listOf(
                        "$inst".asValue,
                        "${inst.lhv}".asValue,
                        "${inst.rhv}".asValue,
                        inst.wrapped(this),
                        inst.lhv.wrapped(this),
                        inst.rhv.wrapped(this)
                    )
                )
            )
        }
        inst.insertAfter(instrumented.mapLocation())
    }

    override fun visitBranchInst(inst: BranchInst) {
        val branchMethod = collectorClass.getMethod(
            "branch", types.voidType,
            stringType, stringType
        )

        val instrumented = collectorClass.interfaceCall(
            branchMethod, traceCollector,
            listOf(
                "$inst".asValue,
                "${inst.cond}".asValue
            )
        )
        inst.insertBefore(instrumented.mapLocation())
    }

    override fun visitCallInst(inst: CallInst) {
        val callMethod = collectorClass.getMethod(
            "call", types.voidType,
            stringType,
            stringType, stringType, listType, stringType,
            stringType, stringType, listType,
            listType
        )

        val calledMethod = inst.method
        val klass = calledMethod.klass

        val instrumented = buildList {
            if (!inst.isStatic && !inst.method.isConstructor) {
                addAll(addNullityConstraint(inst, inst.callee))
                if (inst.opcode != CallOpcode.SPECIAL) {
                    addAll(addTypeConstraints(inst, inst.callee))
                }
            }

            val arrayListKlass = cm.arrayListClass
            val initMethod = arrayListKlass.getMethod("<init>", types.voidType)
            val addMethod = arrayListKlass.getMethod("add", types.boolType, objectType)

            val argTypesList = arrayListType.new().also { add(it) }
            add(arrayListKlass.specialCall(initMethod, argTypesList, emptyList()))
            for (arg in calledMethod.argTypes) {
                add(
                    arrayListKlass.virtualCall(
                        addMethod, argTypesList, listOf(arg.asmDesc.asValue)
                    )
                )
            }

            val argumentList = arrayListType.new().also { add(it) }
            add(arrayListKlass.specialCall(initMethod, argumentList, emptyList()))
            for (arg in inst.args) {
                add(
                    arrayListKlass.virtualCall(
                        addMethod, argumentList, listOf("$arg".asValue)
                    )
                )
            }

            val concreteArgumentsList = arrayListType.new().also { add(it) }
            add(arrayListKlass.specialCall(initMethod, concreteArgumentsList, emptyList()))
            for (arg in inst.args) {
                add(
                    arrayListKlass.virtualCall(
                        addMethod, concreteArgumentsList, listOf(arg.wrapped(this))
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
                    listOf(
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
            )
        }
        inst.insertBefore(instrumented.mapLocation())
    }

    override fun visitCastInst(inst: CastInst) {
        val castMethod = collectorClass.getMethod(
            "cast", types.voidType,
            stringType, stringType,
            objectType, objectType
        )
        val before = buildList {
            if (inst.type.isReference) addAll(addNullityConstraint(inst, inst.operand))
            if (inst.type.isReference) addAll(addTypeConstraints(inst, inst.operand, inst.type))
        }
        val after = buildList {
            add(
                collectorClass.interfaceCall(
                    castMethod, traceCollector,
                    listOf(
                        "$inst".asValue,
                        "${inst.operand}".asValue,
                        inst.wrapped(this),
                        inst.operand.wrapped(this)
                    )
                )
            )
        }
        inst.insertBefore(before.mapLocation())
        inst.insertAfter(after.mapLocation())
    }

    override fun visitCatchInst(inst: CatchInst) {
        val catchMethod = collectorClass.getMethod(
            "catch", types.voidType,
            stringType, objectType
        )

        val instrumented = buildList {
            add(
                collectorClass.interfaceCall(
                    catchMethod, traceCollector,
                    listOf(
                        "$inst".asValue,
                        inst
                    )
                )
            )
            // we need to manually handle all the phi insts of a catch block
            for (phi in inst.parent) {
                when (phi) {
                    is PhiInst -> addAll(buildPhi(phi))
                    else -> break
                }
            }
        }
        inst.insertAfter(instrumented.mapLocation())
    }

    override fun visitCmpInst(inst: CmpInst) {
        val cmpMethod = collectorClass.getMethod(
            "cmp", types.voidType,
            stringType, stringType, stringType,
            objectType, objectType
        )

        val instrumented = buildList {
            add(
                collectorClass.interfaceCall(
                    cmpMethod, traceCollector,
                    listOf(
                        "$inst".asValue,
                        "${inst.lhv}".asValue,
                        "${inst.rhv}".asValue,
                        inst.lhv.wrapped(this),
                        inst.rhv.wrapped(this)
                    )
                )
            )
        }
        inst.insertAfter(instrumented.mapLocation())
    }

    override fun visitEnterMonitorInst(inst: EnterMonitorInst) {
        val enterMonitorMethod = collectorClass.getMethod(
            "enterMonitor", types.voidType,
            stringType, stringType, objectType
        )
        val instrumented = collectorClass.interfaceCall(
            enterMonitorMethod, traceCollector,
            listOf(
                "$inst".asValue,
                "${inst.owner}".asValue,
                inst.owner
            )
        )
        inst.insertAfter(instrumented.mapLocation())
    }

    override fun visitExitMonitorInst(inst: ExitMonitorInst) {
        val exitMonitorMethod = collectorClass.getMethod(
            "exitMonitor", types.voidType,
            stringType, stringType, objectType
        )
        val instrumented = collectorClass.interfaceCall(
            exitMonitorMethod, traceCollector,
            listOf(
                "$inst".asValue,
                "${inst.owner}".asValue,
                inst.owner
            )
        )
        inst.insertAfter(instrumented.mapLocation())
    }

    override fun visitFieldLoadInst(inst: FieldLoadInst) {
        val fieldLoadMethod = collectorClass.getMethod(
            "fieldLoad", types.voidType,
            stringType, stringType, stringType,
            stringType, stringType,
            objectType, objectType
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
                    listOf(
                        "$inst".asValue,
                        owner,
                        fieldKlass,
                        fieldName,
                        fieldType,
                        inst.wrapped(this),
                        concreteOwner.wrapped(this)
                    )
                )
            )
        }
        inst.insertBefore(before.mapLocation())
        inst.insertAfter(after.mapLocation())
    }

    override fun visitFieldStoreInst(inst: FieldStoreInst) {
        val fieldStoreMethod = collectorClass.getMethod(
            "fieldStore", types.voidType,
            stringType,
            stringType, stringType, stringType,
            stringType, stringType,
            objectType, objectType
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
                    listOf(
                        "$inst".asValue,
                        owner,
                        fieldKlass,
                        fieldName,
                        fieldType,
                        "${inst.value}".asValue,
                        inst.value.wrapped(this),
                        defOwner
                    )
                )
            )
        }
        inst.insertBefore(before.mapLocation())
        inst.insertAfter(after.mapLocation())
    }

    override fun visitInstanceOfInst(inst: InstanceOfInst) {
        val instanceOfMethod = collectorClass.getMethod(
            "instanceOf", types.voidType,
            stringType, stringType,
            objectType, objectType
        )

        val instrumented = buildList {
            add(
                collectorClass.interfaceCall(
                    instanceOfMethod, traceCollector,
                    listOf(
                        "$inst".asValue,
                        "${inst.operand}".asValue,
                        inst.wrapped(this),
                        inst.operand.wrapped(this)
                    )
                )
            )
        }
        inst.insertAfter(instrumented.mapLocation())
    }

    override fun visitInvokeDynamicInst(inst: InvokeDynamicInst) {
        val invokeDynamicMethod = collectorClass.getMethod(
            "invokeDynamic", types.voidType,
            stringType, listType,
            objectType, listType
        )

        val instrumented = buildList {
            val arrayListKlass = cm.arrayListClass
            val initMethod = arrayListKlass.getMethod("<init>", types.voidType)
            val addMethod = arrayListKlass.getMethod("add", types.boolType, objectType)
            val args = arrayListType.new().also { add(it) }
            add(arrayListKlass.specialCall(initMethod, args, emptyList()))
            for (arg in inst.args) {
                add(
                    arrayListKlass.virtualCall(
                        addMethod, args, listOf("$arg".asValue)
                    )
                )
            }

            val concreteArgs = arrayListType.new().also { add(it) }
            add(arrayListKlass.specialCall(initMethod, concreteArgs, emptyList()))
            for (arg in inst.args) {
                add(
                    arrayListKlass.virtualCall(
                        addMethod, concreteArgs, listOf(arg.wrapped(this))
                    )
                )
            }

            add(
                collectorClass.interfaceCall(
                    invokeDynamicMethod, traceCollector,
                    listOf(
                        "$inst".asValue,
                        args,
                        inst.wrapped(this),
                        concreteArgs
                    )
                )
            )
        }
        inst.insertAfter(instrumented.mapLocation())
    }

    override fun visitJumpInst(inst: JumpInst) {
        val jumpMethod = collectorClass.getMethod(
            "jump", types.voidType, stringType
        )

        val instrumented = collectorClass.interfaceCall(
            jumpMethod, traceCollector, listOf("$inst".asValue)
        )
        inst.insertBefore(instrumented.mapLocation())
    }

    override fun visitNewArrayInst(inst: NewArrayInst) {
        val newArrayMethod = collectorClass.getMethod(
            "newArray", types.voidType,
            stringType, listType,
            objectType, listType
        )

        val instrumented = buildList {
            val arrayListKlass = cm.arrayListClass
            val initMethod = arrayListKlass.getMethod("<init>", types.voidType)
            val addMethod = arrayListKlass.getMethod("add", types.boolType, objectType)
            val dimensions = arrayListType.new().also { add(it) }
            add(arrayListKlass.specialCall(initMethod, dimensions, emptyList()))
            for (dimension in inst.dimensions) {
                add(
                    arrayListKlass.virtualCall(
                        addMethod, dimensions, listOf("$dimension".asValue)
                    )
                )
            }

            val concreteDimensions = arrayListType.new().also { add(it) }
            add(arrayListKlass.specialCall(initMethod, concreteDimensions, emptyList()))
            for (dimension in inst.dimensions) {
                add(
                    arrayListKlass.virtualCall(
                        addMethod, concreteDimensions, listOf(dimension.wrapped(this))
                    )
                )
            }

            for (dimension in inst.dimensions) {
                addAll(addArrayLengthConstraints(inst, dimension))
            }

            add(
                collectorClass.interfaceCall(
                    newArrayMethod, traceCollector,
                    listOf(
                        "$inst".asValue,
                        dimensions,
                        inst,
                        concreteDimensions
                    )
                )
            )
        }
        inst.insertAfter(instrumented.mapLocation())
    }

    override fun visitNewInst(inst: NewInst) {
        val newMethod = collectorClass.getMethod(
            "new", types.voidType, stringType
        )

        val instrumented = collectorClass.interfaceCall(
            newMethod, traceCollector, listOf("$inst".asValue)
        )
        inst.insertAfter(instrumented.mapLocation())
    }

    override fun visitPhiInst(inst: PhiInst) {
        // if phi is part of a catch block, we need to handle the `CatchInst` first
        if (inst.parent is CatchBlock) return
        inst.insertAfter(buildPhi(inst).mapLocation())
    }

    private fun buildPhi(inst: PhiInst): List<Instruction> = buildList {
        val phiMethod = collectorClass.getMethod(
            "phi", types.voidType, stringType, objectType
        )
        add(
            collectorClass.interfaceCall(
                phiMethod, traceCollector,
                listOf(
                    "$inst".asValue,
                    inst.wrapped(this)
                )
            )
        )
    }

    override fun visitReturnInst(inst: ReturnInst) {
        val returnMethod = collectorClass.getMethod(
            "ret", types.voidType,
            stringType, stringType, objectType
        )


        val (returnValue, concreteValue) = when {
            inst.hasReturnValue -> "${inst.returnValue}".asValue to inst.returnValue
            else -> values.nullConstant to values.nullConstant
        }
        val instrumented = buildList {
            if (inst.hasReturnValue) {
                addAll(track(inst.returnValue))
            }
            val currentMethod = inst.parent.method
            if (!currentMethod.isStatic) {
                addAll(track(values.getThis(currentMethod.klass)))
            }
            for ((index, type) in currentMethod.argTypes.withIndex()) {
                addAll(track(values.getArgument(index, currentMethod, type)))
            }
            add(
                collectorClass.interfaceCall(
                    returnMethod, traceCollector,
                    listOf(
                        "$inst".asValue,
                        returnValue,
                        concreteValue.wrapped(this)
                    )
                )
            )
        }
        inst.insertBefore(instrumented.mapLocation())
    }

    override fun visitSwitchInst(inst: SwitchInst) {
        val switchMethod = collectorClass.getMethod(
            "switch", types.voidType,
            stringType, stringType, objectType
        )

        val instrumented = buildList {
            add(
                collectorClass.interfaceCall(
                    switchMethod, traceCollector,
                    listOf(
                        "$inst".asValue,
                        "${inst.key}".asValue,
                        inst.key.wrapped(this)
                    )
                )
            )
        }
        inst.insertBefore(instrumented.mapLocation())
    }

    override fun visitTableSwitchInst(inst: TableSwitchInst) {
        val tableSwitchMethod = collectorClass.getMethod(
            "tableSwitch", types.voidType,
            stringType, stringType, objectType
        )

        val instrumented = buildList {
            add(
                collectorClass.interfaceCall(
                    tableSwitchMethod, traceCollector,
                    listOf(
                        "$inst".asValue,
                        "${inst.index}".asValue,
                        inst.index.wrapped(this)
                    )
                )
            )
        }
        inst.insertBefore(instrumented.mapLocation())
    }

    override fun visitThrowInst(inst: ThrowInst) {
        val throwMethod = collectorClass.getMethod(
            "throwing", types.voidType,
            stringType, stringType, objectType
        )

        val instrumented = collectorClass.interfaceCall(
            throwMethod, traceCollector,
            listOf(
                "$inst".asValue,
                "${inst.throwable}".asValue,
                inst.throwable
            )
        )
        inst.insertBefore(instrumented.mapLocation())
    }

    override fun visitUnaryInst(inst: UnaryInst) {
        val unaryMethod = collectorClass.getMethod(
            "unary", types.voidType,
            stringType, stringType,
            objectType, objectType
        )

        val before = when (inst.opcode) {
            UnaryOpcode.LENGTH -> addNullityConstraint(inst, inst.operand)
            else -> emptyList()
        }
        val after = buildList {
            add(
                collectorClass.interfaceCall(
                    unaryMethod, traceCollector,
                    listOf(
                        "$inst".asValue,
                        "${inst.operand}".asValue,
                        inst.wrapped(this),
                        inst.operand.wrapped(this)
                    )
                )
            )
        }
        inst.insertBefore(before.mapLocation())
        inst.insertAfter(after.mapLocation())
    }

    private fun track(value: Value): List<Instruction> = buildList {
        val trackMethod = collectorClass.getMethod(
            "track", types.voidType, stringType, objectType
        )
        add(
            collectorClass.interfaceCall(
                trackMethod, traceCollector,
                listOf(
                    "$value".asValue,
                    value.wrapped(this)
                )
            )
        )
    }

    private fun addNullityConstraint(inst: Instruction, value: Value): List<Instruction> = buildList {
        if (inst.parent.method.isConstructor && value is ThisRef) return@buildList

        val addNullityConstraintsMethod = collectorClass.getMethod(
            "addNullityConstraints", types.voidType,
            stringType, stringType, objectType
        )

        add(
            collectorClass.interfaceCall(
                addNullityConstraintsMethod, traceCollector,
                listOf(
                    "$inst".asValue,
                    "$value".asValue,
                    value.wrapped(this)
                )
            )
        )
    }

    private fun addTypeConstraints(inst: Instruction, value: Value): List<Instruction> = buildList {
        val addTypeConstraintsMethod = collectorClass.getMethod(
            "addTypeConstraints", types.voidType,
            stringType, stringType, objectType
        )

        add(
            collectorClass.interfaceCall(
                addTypeConstraintsMethod, traceCollector,
                listOf(
                    "$inst".asValue,
                    "$value".asValue,
                    value.wrapped(this)
                )
            )
        )
    }

    private fun addTypeConstraints(inst: Instruction, value: Value, type: Type): List<Instruction> = buildList {
        val addTypeConstraintsMethod = collectorClass.getMethod(
            "addTypeConstraints", types.voidType,
            stringType, stringType, stringType, objectType
        )

        add(
            collectorClass.interfaceCall(
                addTypeConstraintsMethod, traceCollector,
                listOf(
                    "$inst".asValue,
                    "$value".asValue,
                    type.name.asValue,
                    value.wrapped(this)
                )
            )
        )
    }

    private fun addArrayIndexConstraints(inst: Instruction, array: Value, index: Value): List<Instruction> = buildList {
        val addArrayIndexConstraintsMethod = collectorClass.getMethod(
            "addArrayIndexConstraints", types.voidType,
            stringType,
            stringType, stringType,
            objectType, objectType
        )

        add(
            collectorClass.interfaceCall(
                addArrayIndexConstraintsMethod, traceCollector,
                listOf(
                    "$inst".asValue,
                    "$array".asValue,
                    "$index".asValue,
                    array.wrapped(this),
                    index.wrapped(this)
                )
            )
        )
    }

    private fun addArrayLengthConstraints(inst: Instruction, length: Value): List<Instruction> = buildList {
        val addArrayIndexConstraintsMethod = collectorClass.getMethod(
            "addArrayLengthConstraints", types.voidType,
            stringType,
            stringType, objectType
        )

        add(
            collectorClass.interfaceCall(
                addArrayIndexConstraintsMethod, traceCollector,
                listOf(
                    "$inst".asValue,
                    "$length".asValue,
                    length.wrapped(this)
                )
            )
        )
    }

    private fun getNewCollector(): Instruction {
        val getter = collectorProxyClass.getMethod("currentCollector", cm.type.getRefType(collectorClass))
        return getter.staticCall(collectorProxyClass, "collector", emptyList())
    }

    private fun setNewCollector(collector: Value): Instruction {
        val setter = collectorProxyClass.getMethod(
            "setCurrentCollector",
            cm.type.voidType,
            cm.type.getRefType(collectorClass)
        )

        return setter.staticCall(collectorProxyClass, listOf(collector))
    }

    private fun disableCollector(): Instruction {
        val disabler = collectorProxyClass.getMethod("disableCollector", cm.type.voidType)
        return disabler.staticCall(collectorProxyClass, listOf())
    }

    private fun Class.virtualCall(
        method: Method,
        instance: Value,
        args: List<Value>
    ) = method.virtualCall(this, instance, args)

    private fun Class.interfaceCall(
        method: Method,
        instance: Value,
        args: List<Value>
    ) = method.interfaceCall(this, instance, args)

    private fun Class.specialCall(
        method: Method,
        instance: Value,
        args: List<Value>
    ) = method.specialCall(this, instance, args)

    private fun Value.wrapped(list: MutableList<Instruction>): Value = when {
        this.type.isPrimitive -> wrapUpValue(this).also {
            list += it
        }

        else -> this
    }
}
