package org.jetbrains.research.kex.asm.transform

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.trace.symbolic.InstructionTraceCollector
import org.jetbrains.research.kex.trace.symbolic.TraceCollectorProxy
import org.jetbrains.research.kex.util.insertAfter
import org.jetbrains.research.kex.util.insertBefore
import org.jetbrains.research.kex.util.wrapValue
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.MethodDesc
import org.jetbrains.research.kfg.ir.value.*
import org.jetbrains.research.kfg.ir.value.instruction.*
import org.jetbrains.research.kfg.type.TypeFactory
import org.jetbrains.research.kfg.visitor.MethodVisitor
import org.jetbrains.research.kthelper.collection.MutableBuilder
import org.jetbrains.research.kthelper.collection.buildList

class SymbolicTraceCollector(
    val executionContext: ExecutionContext,
    val ignores: Set<Package> = setOf()
) : MethodVisitor, InstructionBuilder {
    override val ctx: UsageContext = EmptyUsageContext
    override val cm get() = executionContext.cm

    override val instructions: InstructionFactory
        get() = cm.instruction
    override val types: TypeFactory
        get() = cm.type
    override val values: ValueFactory
        get() = cm.value

    private val collectorClass = cm[InstructionTraceCollector::class.java.canonicalName.replace('.', '/')]
    private lateinit var traceCollector: Instruction

    override fun cleanup() {}

    override fun visit(method: Method) {
        if (!method.hasBody || method.isStaticInitializer) return
        if (ignores.any { it.isParent(method.klass.pkg) }) return

        val methodEntryInstructions: List<Instruction> = buildList {
            traceCollector = getNewCollector()
            +traceCollector
            val entryMethod = collectorClass.getMethod(
                "methodEnter", types.voidType,
                types.stringType, types.stringType, types.listType,
                types.stringType, types.objectType, types.listType
            )

            val arrayListKlass = cm.arrayListClass
            val initMethod = arrayListKlass.getMethod("<init>", types.voidType)
            val addMethod = arrayListKlass.getMethod("add", types.boolType, types.objectType)

            val argTypesList = types.arrayListType.new().also { +it }
            +arrayListKlass.specialCall(initMethod, argTypesList)

            val argumentList = types.arrayListType.new().also { +it }
            +arrayListKlass.specialCall(initMethod, argumentList)

            for ((index, arg) in method.argTypes.withIndex()) {
                +arrayListKlass.virtualCall(
                    addMethod, argTypesList, arg.asmDesc.asValue
                )
                val argument = values.getArgument(index, method, arg)
                +arrayListKlass.virtualCall(
                    addMethod, argumentList, argument.wrapped(this)
                )
            }
            val instance = when {
                method.isStatic || method.isConstructor -> values.nullConstant
                else -> values.getThis(method.klass)
            }

            +collectorClass.interfaceCall(
                entryMethod,
                traceCollector,
                method.klass.fullName.asValue,
                method.name.asValue,
                argTypesList,
                method.returnType.asmDesc.asValue,
                instance,
                argumentList
            )
        }
        super.visit(method)
        method.entry.first().insertBefore(methodEntryInstructions)
    }

    override fun visitArrayLoadInst(inst: ArrayLoadInst) {
        val arrayLoadMethod = collectorClass.getMethod(
            "arrayLoad", types.voidType,
            types.stringType, types.stringType, types.stringType,
            types.objectType, types.objectType, types.objectType
        )

        val instrumented = buildList<Instruction> {
            +collectorClass.interfaceCall(
                arrayLoadMethod, traceCollector,
                "$inst".asValue, "${inst.arrayRef}".asValue, "${inst.index}".asValue,
                inst.wrapped(this), inst.arrayRef, inst.index.wrapped(this)
            )
        }
        inst.insertAfter(instrumented)
    }

    override fun visitArrayStoreInst(inst: ArrayStoreInst) {
        val arrayStoreMethod = collectorClass.getMethod(
            "arrayStore", types.voidType,
            types.stringType, types.stringType, types.stringType, types.stringType,
            types.objectType, types.objectType, types.objectType
        )

        val instrumented = buildList<Instruction> {
            +collectorClass.interfaceCall(
                arrayStoreMethod, traceCollector,
                "$inst".asValue, "${inst.arrayRef}".asValue, "${inst.index}".asValue, "${inst.value}".asValue,
                inst.arrayRef, inst.index.wrapped(this), inst.value.wrapped(this)
            )
        }
        inst.insertAfter(instrumented)
    }

    override fun visitBinaryInst(inst: BinaryInst) {
        val binaryMethod = collectorClass.getMethod(
            "binary", types.voidType,
            types.stringType, types.stringType, types.stringType,
            types.objectType, types.objectType, types.objectType
        )

        val instrumented = buildList<Instruction> {
            +collectorClass.interfaceCall(
                binaryMethod, traceCollector,
                "$inst".asValue, "${inst.lhv}".asValue, "${inst.rhv}".asValue,
                inst.wrapped(this), inst.lhv.wrapped(this), inst.rhv.wrapped(this)
            )
        }
        inst.insertAfter(instrumented)
    }

    override fun visitBranchInst(inst: BranchInst) {
        val branchMethod = collectorClass.getMethod(
            "branch", types.voidType,
            types.stringType, types.stringType
        )

        val instrumented = buildList<Instruction> {
            +collectorClass.interfaceCall(
                branchMethod, traceCollector,
                "$inst".asValue, "${inst.cond}".asValue
            )
        }
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

        val instrumented = buildList<Instruction> {
            val arrayListKlass = cm.arrayListClass
            val initMethod = arrayListKlass.getMethod("<init>", types.voidType)
            val addMethod = arrayListKlass.getMethod("add", types.boolType, types.objectType)

            val argTypesList = types.arrayListType.new().also { +it }
            +arrayListKlass.specialCall(initMethod, argTypesList)
            for (arg in calledMethod.argTypes) {
                +arrayListKlass.virtualCall(
                    addMethod, argTypesList, arg.asmDesc.asValue
                )
            }

            val argumentList = types.arrayListType.new().also { +it }
            +arrayListKlass.specialCall(initMethod, argumentList)
            for (arg in inst.args) {
                +arrayListKlass.virtualCall(
                    addMethod, argumentList, "$arg".asValue
                )
            }

            val concreteArgumentsList = types.arrayListType.new().also { +it }
            +arrayListKlass.specialCall(initMethod, concreteArgumentsList)
            for (arg in inst.args) {
                +arrayListKlass.virtualCall(
                    addMethod, concreteArgumentsList, arg.wrapped(this)
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

            +collectorClass.interfaceCall(
                callMethod, traceCollector,
                "$inst".asValue,
                klass.fullName.asValue, calledMethod.name.asValue, argTypesList, calledMethod.returnType.asmDesc.asValue,
                returnValue, callee, argumentList,
                concreteArgumentsList
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
        val instrumented = buildList<Instruction> {
            +collectorClass.interfaceCall(
                castMethod, traceCollector,
                "$inst".asValue, "${inst.operand}".asValue,
                inst.wrapped(this), inst.operand.wrapped(this)
            )
        }
        inst.insertAfter(instrumented)
    }

    override fun visitCatchInst(inst: CatchInst) {
        val catchMethod = collectorClass.getMethod(
            "catch", types.voidType,
            types.stringType, types.objectType
        )

        val instrumented = buildList<Instruction> {
            +collectorClass.interfaceCall(
                catchMethod, traceCollector,
                "$inst".asValue, inst
            )
        }
        inst.insertAfter(instrumented)
    }

    override fun visitCmpInst(inst: CmpInst) {
        val cmpMethod = collectorClass.getMethod(
            "cmp", types.voidType,
            types.stringType, types.stringType, types.stringType,
            types.objectType, types.objectType
        )

        val instrumented = buildList<Instruction> {
            +collectorClass.interfaceCall(
                cmpMethod, traceCollector,
                "$inst".asValue, "${inst.lhv}".asValue, "${inst.rhv}".asValue,
                inst.lhv.wrapped(this), inst.rhv.wrapped(this)
            )
        }
        inst.insertAfter(instrumented)
    }

    override fun visitEnterMonitorInst(inst: EnterMonitorInst) {
        val enterMonitorMethod = collectorClass.getMethod(
            "enterMonitor", types.voidType,
            types.stringType, types.stringType, types.objectType
        )
        val instrumented = buildList<Instruction> {
            +collectorClass.interfaceCall(
                enterMonitorMethod, traceCollector,
                "$inst".asValue, "${inst.owner}".asValue, inst.owner
            )
        }
        inst.insertAfter(instrumented)
    }

    override fun visitExitMonitorInst(inst: ExitMonitorInst) {
        val exitMonitorMethod = collectorClass.getMethod(
            "exitMonitor", types.voidType,
            types.stringType, types.stringType, types.objectType
        )
        val instrumented = buildList<Instruction> {
            +collectorClass.interfaceCall(
                exitMonitorMethod, traceCollector,
                "$inst".asValue, "${inst.owner}".asValue, inst.owner
            )
        }
        inst.insertAfter(instrumented)
    }

    override fun visitFieldLoadInst(inst: FieldLoadInst) {
        val fieldLoadMethod = collectorClass.getMethod(
            "fieldLoad", types.voidType,
            types.stringType, types.stringType, types.stringType,
            types.stringType, types.stringType,
            types.objectType, types.objectType
        )

        val instrumented = buildList<Instruction> {
            val fieldKlass = inst.field.klass.fullName.asValue
            val fieldName = inst.field.name.asValue
            val fieldType = inst.field.type.asmDesc.asValue
            val (owner, concreteOwner) = when {
                inst.isStatic -> values.nullConstant to values.nullConstant
                else -> "${inst.owner}".asValue to inst.owner
            }

            +collectorClass.interfaceCall(
                fieldLoadMethod, traceCollector,
                "$inst".asValue, owner, fieldKlass, fieldName, fieldType,
                inst.wrapped(this), concreteOwner.wrapped(this)
            )
        }
        inst.insertAfter(instrumented)
    }

    override fun visitFieldStoreInst(inst: FieldStoreInst) {
        val fieldStoreMethod = collectorClass.getMethod(
            "fieldStore", types.voidType,
            types.stringType,
            types.stringType, types.stringType, types.stringType,
            types.stringType, types.stringType,
            types.objectType, types.objectType
        )

        val instrumented = buildList<Instruction> {
            val fieldKlass = inst.field.klass.fullName.asValue
            val fieldName = inst.field.name.asValue
            val fieldType = inst.field.type.asmDesc.asValue
            val (owner, concreteOwner) = when {
                inst.isStatic -> values.nullConstant to values.nullConstant
                else -> "${inst.owner}".asValue to inst.owner
            }
            val defOwner = when {
                inst.hasOwner && inst.owner is ThisRef && inst.parent.parent.isConstructor -> values.nullConstant
                else -> concreteOwner
            }

            +collectorClass.interfaceCall(
                fieldStoreMethod, traceCollector,
                "$inst".asValue,
                owner, fieldKlass, fieldName, fieldType, "${inst.value}".asValue,
                inst.value.wrapped(this), defOwner
            )
        }
        inst.insertAfter(instrumented)
    }

    override fun visitInstanceOfInst(inst: InstanceOfInst) {
        val instanceOfMethod = collectorClass.getMethod(
            "instanceOf", types.voidType,
            types.stringType, types.stringType,
            types.objectType, types.objectType
        )

        val instrumented = buildList<Instruction> {
            +collectorClass.interfaceCall(
                instanceOfMethod, traceCollector,
                "$inst".asValue, "${inst.operand}".asValue,
                inst.wrapped(this), inst.operand.wrapped(this)
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

        val instrumented = buildList<Instruction> {
            val arrayListKlass = cm.arrayListClass
            val initMethod = arrayListKlass.getMethod("<init>", types.voidType)
            val addMethod = arrayListKlass.getMethod("add", types.boolType, types.objectType)
            val args = types.arrayListType.new().also { +it }
            +arrayListKlass.specialCall(initMethod, args)
            for (arg in inst.args) {
                +arrayListKlass.virtualCall(
                    addMethod, args, "$arg".asValue
                )
            }

            val concreteArgs = types.arrayListType.new().also { +it }
            +arrayListKlass.specialCall(initMethod, concreteArgs)
            for (arg in inst.args) {
                +arrayListKlass.virtualCall(
                    addMethod, concreteArgs, arg.wrapped(this)
                )
            }

            +collectorClass.interfaceCall(
                invokeDynamicMethod, traceCollector,
                "$inst".asValue, args,
                inst.wrapped(this), concreteArgs
            )
        }
        inst.insertAfter(instrumented)
    }

    override fun visitJumpInst(inst: JumpInst) {
        val jumpMethod = collectorClass.getMethod(
            "jump", types.voidType, types.stringType
        )

        val instrumented = buildList<Instruction> {
            +collectorClass.interfaceCall(
                jumpMethod, traceCollector, "$inst".asValue
            )
        }
        inst.insertBefore(instrumented)
    }

    override fun visitNewArrayInst(inst: NewArrayInst) {
        val newArrayMethod = collectorClass.getMethod(
            "newArray", types.voidType,
            types.stringType, types.listType,
            types.objectType, types.listType
        )

        val instrumented = buildList<Instruction> {
            val arrayListKlass = cm.arrayListClass
            val initMethod = arrayListKlass.getMethod("<init>", types.voidType)
            val addMethod = arrayListKlass.getMethod("add", types.boolType, types.objectType)
            val dimensions = types.arrayListType.new().also { +it }
            +arrayListKlass.specialCall(initMethod, dimensions)
            for (dimension in inst.dimensions) {
                +arrayListKlass.virtualCall(
                    addMethod, dimensions, "$dimension".asValue
                )
            }

            val concreteDimensions = types.arrayListType.new().also { +it }
            +arrayListKlass.specialCall(initMethod, concreteDimensions)
            for (dimension in inst.dimensions) {
                +arrayListKlass.virtualCall(
                    addMethod, concreteDimensions, dimension.wrapped(this)
                )
            }

            +collectorClass.interfaceCall(
                newArrayMethod, traceCollector,
                "$inst".asValue, dimensions,
                inst, concreteDimensions
            )
        }
        inst.insertAfter(instrumented)
    }

    override fun visitNewInst(inst: NewInst) {
        val newMethod = collectorClass.getMethod(
            "new", types.voidType, types.stringType
        )

        val instrumented = buildList<Instruction> {
            +collectorClass.interfaceCall(
                newMethod, traceCollector, "$inst".asValue
            )
        }
        inst.insertAfter(instrumented)
    }

    override fun visitPhiInst(inst: PhiInst) {
        val phiMethod = collectorClass.getMethod(
            "phi", types.voidType, types.stringType, types.objectType
        )

        val instrumented = buildList<Instruction> {
            +collectorClass.interfaceCall(
                phiMethod, traceCollector, "$inst".asValue, inst.wrapped(this)
            )
        }
        inst.insertAfter(instrumented)
    }

    override fun visitReturnInst(inst: ReturnInst) {
        val returnMethod = collectorClass.getMethod(
            "ret", types.voidType,
            types.stringType, types.stringType, types.objectType
        )

        val instrumented = buildList<Instruction> {
            val (returnValue, concreteValue) = when {
                inst.hasReturnValue -> "${inst.returnValue}".asValue to inst.returnValue
                else -> values.nullConstant to values.nullConstant
            }

            +collectorClass.interfaceCall(
                returnMethod, traceCollector,
                "$inst".asValue, returnValue, concreteValue.wrapped(this)
            )
        }
        inst.insertBefore(instrumented)
    }

    override fun visitSwitchInst(inst: SwitchInst) {
        val switchMethod = collectorClass.getMethod(
            "switch", types.voidType,
            types.stringType, types.stringType, types.objectType
        )

        val instrumented = buildList<Instruction> {
            +collectorClass.interfaceCall(
                switchMethod, traceCollector,
                "$inst".asValue, "${inst.key}".asValue, inst.key.wrapped(this)
            )
        }
        inst.insertBefore(instrumented)
    }

    override fun visitTableSwitchInst(inst: TableSwitchInst) {
        val tableSwitchMethod = collectorClass.getMethod(
            "tableSwitch", types.voidType,
            types.stringType, types.stringType, types.objectType
        )

        val instrumented = buildList<Instruction> {
            +collectorClass.interfaceCall(
                tableSwitchMethod, traceCollector,
                "$inst".asValue, "${inst.index}".asValue, inst.index.wrapped(this)
            )
        }
        inst.insertBefore(instrumented)
    }

    override fun visitThrowInst(inst: ThrowInst) {
        val throwMethod = collectorClass.getMethod(
            "throwing", types.voidType,
            types.stringType, types.stringType, types.objectType
        )

        val instrumented = buildList<Instruction> {
            +collectorClass.interfaceCall(
                throwMethod, traceCollector,
                "$inst".asValue, "${inst.throwable}".asValue, inst.throwable
            )
        }
        inst.insertBefore(instrumented)
    }

    override fun visitUnaryInst(inst: UnaryInst) {
        val unaryMethod = collectorClass.getMethod(
            "unary", types.voidType,
            types.stringType, types.stringType,
            types.objectType, types.objectType
        )

        val instrumented = buildList<Instruction> {
            +collectorClass.interfaceCall(
                unaryMethod, traceCollector,
                "$inst".asValue, "${inst.operand}".asValue,
                inst.wrapped(this), inst.operand.wrapped(this)
            )
        }
        inst.insertAfter(instrumented)
    }


    private fun getNewCollector(): Instruction {
        val proxy = cm[TraceCollectorProxy::class.java.canonicalName.replace('.', '/')]
        val getter = proxy.getMethod("currentCollector", MethodDesc(arrayOf(), cm.type.getRefType(collectorClass)))

        return getter.staticCall(proxy, "collector", arrayOf())
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

    private fun Value.wrapped(list: MutableBuilder<Instruction>): Value = when {
        this.type.isPrimary -> wrapValue(this).also {
            list.inner += it
        }
        else -> this
    }
}