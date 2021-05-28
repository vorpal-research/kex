package org.jetbrains.research.kex.asm.transform

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.trace.symbolic.InstructionTraceCollector
import org.jetbrains.research.kex.trace.symbolic.TraceCollectorProxy
import org.jetbrains.research.kex.util.insertBefore
import org.jetbrains.research.kex.util.wrapValue
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.MethodDesc
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.instruction.*
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.visitor.MethodVisitor
import org.jetbrains.research.kthelper.collection.buildList

class SymbolicTraceCollector(
    val ctx: ExecutionContext,
    val ignores: Set<Package> = setOf()
) : MethodVisitor {
    override val cm get() = ctx.cm

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
                types.stringType, types.stringType, types.stringType.asArray,
                types.stringType, types.objectType, types.objectType.asArray
            )
            val sizeVal = method.argTypes.size.asValue
            val stringArray = instructions.getNewArray(types.stringType, sizeVal)
            val argArray = instructions.getNewArray(types.objectType, sizeVal)
            +stringArray
            +argArray
            for ((index, arg) in method.argTypes.withIndex()) {
                val indexVal = index.asValue
                +instructions.getArrayStore(stringArray, indexVal, arg.asmDesc.asValue)
                val argument = values.getArgument(index, method, arg)
                +when {
                    arg.isPrimary -> {
                        val wrapped = instructions.wrapValue(argument)
                        +wrapped
                        instructions.getArrayStore(argArray, indexVal, wrapped)
                    }
                    else -> instructions.getArrayStore(argArray, indexVal, argument)
                }
            }
            val instance = when {
                method.isStatic || method.isConstructor -> values.nullConstant
                else -> values.getThis(method.klass)
            }

            +collectorClass.virtualCall(
                entryMethod,
                traceCollector,
                method.klass.fullName.asValue,
                method.name.asValue,
                stringArray,
                method.returnType.asmDesc.asValue,
                instance,
                argArray
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
            +collectorClass.virtualCall(
                arrayLoadMethod, traceCollector,
                "$inst".asValue, "${inst.arrayRef}".asValue, "${inst.index}".asValue,
                inst, inst.arrayRef, inst.index
            )
        }
        inst.insertBefore(instrumented)
    }

    override fun visitArrayStoreInst(inst: ArrayStoreInst) {
        val arrayStoreMethod = collectorClass.getMethod(
            "arrayStore", types.voidType,
            types.stringType, types.stringType, types.stringType,
            types.objectType, types.objectType, types.objectType
        )

        val instrumented = buildList<Instruction> {
            +collectorClass.virtualCall(
                arrayStoreMethod, traceCollector,
                "${inst.arrayRef}".asValue, "${inst.index}".asValue, "${inst.value}".asValue,
                inst.arrayRef, inst.index, inst.value
            )
        }
        inst.insertBefore(instrumented)
    }

    override fun visitBinaryInst(inst: BinaryInst) {
        val binaryMethod = collectorClass.getMethod(
            "binary", types.voidType,
            types.stringType, types.stringType, types.stringType,
            types.objectType, types.objectType, types.objectType
        )

        val instrumented = buildList<Instruction> {
            +collectorClass.virtualCall(
                binaryMethod, traceCollector,
                "$inst".asValue, "${inst.lhv}".asValue, "${inst.rhv}".asValue,
                inst, inst.lhv, inst.rhv
            )
        }
        inst.insertBefore(instrumented)
    }

    override fun visitBranchInst(inst: BranchInst) {
        val branchMethod = collectorClass.getMethod(
            "branch", types.voidType,
            types.stringType, types.stringType, types.objectType
        )

        val instrumented = buildList<Instruction> {
            +collectorClass.virtualCall(
                branchMethod, traceCollector,
                "${inst.cond}".asValue, "${inst.parent.name}".asValue, inst.cond
            )
        }
        inst.insertBefore(instrumented)
    }

    override fun visitCallInst(inst: CallInst) {
        val callMethod = collectorClass.getMethod(
            "call", types.voidType,
            types.stringType, types.stringType, types.stringType.asArray, types.stringType,
            types.stringType, types.stringType, types.listType,
            types.objectType, types.objectType, types.listType
        )

        val calledMethod = inst.method
        val klass = calledMethod.klass

        val instrumented = buildList<Instruction> {
            val arrayListKlass = cm.arrayListClass
            val addMethod = arrayListKlass.getMethod("add", types.boolType, types.objectType)

            val argTypesList = instructions.getNew(types.arrayListType)
            +argTypesList
            for (arg in calledMethod.argTypes) {
                +arrayListKlass.virtualCall(
                    addMethod, argTypesList, arg.asmDesc.asValue
                )
            }

            val argumentList = instructions.getNew(types.arrayListType)
            +argumentList
            for (arg in inst.args) {
                +arrayListKlass.virtualCall(
                    addMethod, argumentList, "$arg".asValue
                )
            }

            val concreteArgumentsList = instructions.getNew(types.arrayListType)
            +concreteArgumentsList
            for (arg in inst.args) {
                +arrayListKlass.virtualCall(
                    addMethod, concreteArgumentsList, arg
                )
            }

            val (returnValue, concreteReturnValue) = when {
                inst.isNameDefined -> "$inst".asValue to inst
                else -> values.nullConstant to values.nullConstant
            }
            val (callee, concreteCallee) = when {
                inst.isStatic -> values.nullConstant to values.nullConstant
                else -> "${inst.callee}".asValue to inst.callee
            }

            +collectorClass.virtualCall(
                callMethod, traceCollector,
                klass.fullName.asValue, callMethod.name.asValue, argTypesList, calledMethod.returnType.asmDesc.asValue,
                returnValue, callee, argumentList,
                concreteReturnValue, concreteCallee, concreteArgumentsList
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
            +collectorClass.virtualCall(
                castMethod, traceCollector,
                "$inst".asValue, "${inst.operand}".asValue,
                inst, inst.operand
            )
        }
        inst.insertBefore(instrumented)
    }

    override fun visitCatchInst(inst: CatchInst) {
        val catchMethod = collectorClass.getMethod(
            "catch", types.voidType,
            types.stringType, types.objectType
        )

        val instrumented = buildList<Instruction> {
            +collectorClass.virtualCall(
                catchMethod, traceCollector,
                "$inst".asValue, inst
            )
        }
        inst.insertBefore(instrumented)
    }

    override fun visitCmpInst(inst: CmpInst) {
        val cmpMethod = collectorClass.getMethod(
            "cmp", types.voidType,
            types.stringType, types.stringType, types.stringType,
            types.objectType, types.objectType, types.objectType
        )

        val instrumented = buildList<Instruction> {
            +collectorClass.virtualCall(
                cmpMethod, traceCollector,
                "$inst".asValue, "${inst.lhv}".asValue, "${inst.rhv}".asValue,
                inst, inst.lhv, inst.rhv
            )
        }
        inst.insertBefore(instrumented)
    }

    override fun visitEnterMonitorInst(inst: EnterMonitorInst) {
        val enterMonitorMethod = collectorClass.getMethod(
            "enterMonitor", types.voidType,
            types.stringType, types.objectType
        )
        val instrumented = buildList<Instruction> {
            +collectorClass.virtualCall(
                enterMonitorMethod, traceCollector,
                "${inst.owner}".asValue, inst.owner
            )
        }
        inst.insertBefore(instrumented)
    }

    override fun visitExitMonitorInst(inst: ExitMonitorInst) {
        val exitMonitorMethod = collectorClass.getMethod(
            "exitMonitor", types.voidType,
            types.stringType, types.objectType
        )
        val instrumented = buildList<Instruction> {
            +collectorClass.virtualCall(
                exitMonitorMethod, traceCollector,
                "${inst.owner}".asValue, inst.owner
            )
        }
        inst.insertBefore(instrumented)
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

            +collectorClass.virtualCall(
                fieldLoadMethod, traceCollector,
                "$inst".asValue, owner, fieldKlass, fieldName, fieldType,
                inst, concreteOwner
            )
        }
        inst.insertBefore(instrumented)
    }

    override fun visitFieldStoreInst(inst: FieldStoreInst) {
        val fieldStoreMethod = collectorClass.getMethod(
            "fieldStore", types.voidType,
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

            +collectorClass.virtualCall(
                fieldStoreMethod, traceCollector,
                owner, fieldKlass, fieldName, fieldType, "${inst.value}".asValue,
                inst.value, concreteOwner
            )
        }
        inst.insertBefore(instrumented)
    }

    override fun visitInstanceOfInst(inst: InstanceOfInst) {
        val instanceOfMethod = collectorClass.getMethod(
            "instanceOf", types.voidType,
            types.stringType, types.stringType,
            types.objectType, types.objectType
        )

        val instrumented = buildList<Instruction> {
            +collectorClass.virtualCall(
                instanceOfMethod, traceCollector,
                "$inst".asValue, "${inst.operand}".asValue,
                inst, inst.operand
            )
        }
        inst.insertBefore(instrumented)
    }

    override fun visitJumpInst(inst: JumpInst) {
        val jumpMethod = collectorClass.getMethod(
            "jump", types.voidType, types.stringType, types.stringType
        )

        val instrumented = buildList<Instruction> {
            val currentBlock = "${inst.parent.name}".asValue
            val targetBlock = "${inst.successor.name}".asValue
            +collectorClass.virtualCall(
                jumpMethod, traceCollector, currentBlock, targetBlock
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
            val addMethod = arrayListKlass.getMethod("add", types.boolType, types.objectType)
            val dimensions = instructions.getNew(types.arrayListType)
            +dimensions
            for (dimension in inst.dimensions) {
                +arrayListKlass.virtualCall(
                    addMethod, dimensions, "$dimension".asValue
                )
            }

            val concreteDimensions = instructions.getNew(types.arrayListType)
            +concreteDimensions
            for (dimension in inst.dimensions) {
                +arrayListKlass.virtualCall(
                    addMethod, concreteDimensions, dimension
                )
            }

            +collectorClass.virtualCall(
                newArrayMethod, traceCollector,
                "$inst".asValue, dimensions,
                inst, concreteDimensions
            )
        }
        inst.insertBefore(instrumented)
    }

    override fun visitNewInst(inst: NewInst) {
        val newMethod = collectorClass.getMethod(
            "new", types.voidType, types.stringType, types.objectType
        )

        val instrumented = buildList<Instruction> {
            +collectorClass.virtualCall(
                newMethod, traceCollector, "$inst".asValue, inst
            )
        }
        inst.insertBefore(instrumented)
    }

    override fun visitPhiInst(inst: PhiInst) {
        val phiMethod = collectorClass.getMethod(
            "phi", types.voidType, types.stringType, types.objectType
        )

        val instrumented = buildList<Instruction> {
            +collectorClass.virtualCall(
                phiMethod, traceCollector, "$inst".asValue, inst
            )
        }
        inst.insertBefore(instrumented)
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

            +collectorClass.virtualCall(
                returnMethod, traceCollector,
                returnValue, "${inst.parent.name}".asValue, concreteValue
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
            +collectorClass.virtualCall(
                switchMethod, traceCollector,
                "${inst.key}".asValue, "${inst.parent.name}".asValue, inst.key
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
            +collectorClass.virtualCall(
                tableSwitchMethod, traceCollector,
                "${inst.index}".asValue, "${inst.parent.name}".asValue, inst.index
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
            +collectorClass.virtualCall(
                throwMethod, traceCollector,
                "${inst.parent.name}".asValue, "${inst.throwable}".asValue, inst.throwable
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
            +collectorClass.virtualCall(
                unaryMethod, traceCollector,
                "$inst".asValue, "${inst.operand}".asValue,
                inst, inst.operand
            )
        }
        inst.insertBefore(instrumented)
    }


    private fun getNewCollector(): Instruction {
        val proxy = cm[TraceCollectorProxy::class.java.canonicalName.replace('.', '/')]
        val getter = proxy.getMethod("currentCollector", MethodDesc(arrayOf(), cm.type.getRefType(collectorClass)))

        return cm.instruction.getCall(CallOpcode.Static(), "collector", getter, proxy, arrayOf())
    }

    private fun Class.virtualCall(
        method: Method,
        instance: Value,
        vararg args: Value
    ) = instructions.getCall(
        CallOpcode.Virtual(), method, this, instance, args.toList().toTypedArray(), false
    )

    private val Boolean.asValue get() = values.getBool(this)
    private val Number.asValue get() = values.getNumber(this)
    private val String.asValue get() = values.getString(this)

    private val Type.asArray get() = types.getArrayType(this)
}