package org.jetbrains.research.kex.asm.transform

import org.jetbrains.research.kex.trace.`object`.TraceCollector
import org.jetbrains.research.kex.trace.`object`.TraceCollectorProxy
import org.jetbrains.research.kex.util.wrapValue
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.MethodDesc
import org.jetbrains.research.kfg.ir.value.EmptyUsageContext
import org.jetbrains.research.kfg.ir.value.UsageContext
import org.jetbrains.research.kfg.ir.value.ValueFactory
import org.jetbrains.research.kfg.ir.value.instruction.*
import org.jetbrains.research.kfg.type.TypeFactory
import org.jetbrains.research.kfg.visitor.MethodVisitor
import org.jetbrains.research.kthelper.collection.buildList

class RuntimeTraceCollector(override val cm: ClassManager) : MethodVisitor, InstructionBuilder {
    override val ctx: UsageContext = EmptyUsageContext
    private val collectorClass = cm[TraceCollector::class.java.canonicalName.replace('.', '/')]
    private lateinit var traceCollector: Instruction

    override val instructions: InstructionFactory
        get() = cm.instruction
    override val types: TypeFactory
        get() = cm.type
    override val values: ValueFactory
        get() = cm.value

    private fun getNewCollector(): Instruction {
        val proxy = cm[TraceCollectorProxy::class.java.canonicalName.replace('.', '/')]
        val getter = proxy.getMethod("currentCollector", MethodDesc(arrayOf(), cm.type.getRefType(collectorClass)))

        return getter.staticCall(proxy, "collector", arrayOf())
    }

    private fun List<Instruction>.insertBefore(inst: Instruction) {
        inst.parent.insertBefore(inst, *this.toTypedArray())
    }

    override fun cleanup() {}

    override fun visitBranchInst(inst: BranchInst) {
        val blockExitInsts = buildList<Instruction> {
            val branchMethod = collectorClass.getMethod(
                "blockBranch",
                MethodDesc(arrayOf(types.stringType, types.objectType, types.objectType), types.voidType)
            )
            val (condition, expected) = when (inst.cond) {
                is CmpInst -> {
                    val cmp = inst.cond as CmpInst
                    val lhv = when {
                        cmp.lhv.type.isPrimary -> wrapValue(cmp.lhv).also { +it }
                        else -> cmp.lhv
                    }
                    val rhv = when {
                        cmp.rhv.type.isPrimary -> wrapValue(cmp.rhv).also { +it }
                        else -> cmp.rhv
                    }
                    lhv to rhv
                }
                else -> {
                    val wrap = wrapValue(inst.cond).also { +it }
                    wrap to values.nullConstant
                }
            }
            +branchMethod.interfaceCall(
                collectorClass,
                traceCollector,
                arrayOf("${inst.parent.name}".asValue, condition, expected)
            )
        }
        inst.parent.insertBefore(inst, *blockExitInsts.toTypedArray())
    }

    override fun visitJumpInst(inst: JumpInst) {
        val blockExitInsts = buildList<Instruction> {
            val jumpMethod = collectorClass.getMethod(
                "blockJump",
                MethodDesc(arrayOf(types.stringType), types.voidType)
            )
            +jumpMethod.interfaceCall(
                collectorClass,
                traceCollector,
                arrayOf("${inst.parent.name}".asValue)
            )
        }
        inst.parent.insertBefore(inst, *blockExitInsts.toTypedArray())
    }

    override fun visitSwitchInst(inst: SwitchInst) = buildList<Instruction> {
        val switchMethod = collectorClass.getMethod(
            "blockSwitch",
            MethodDesc(arrayOf(types.stringType, types.objectType), types.voidType)
        )
        val key = run {
            val key = inst.key
            when {
                key.type.isPrimary -> wrapValue(key).also { +it }
                else -> key
            }
        }
        +switchMethod.interfaceCall(
            collectorClass,
            traceCollector,
            arrayOf("${inst.parent.name}".asValue, key)
        )
    }.insertBefore(inst)

    override fun visitTableSwitchInst(inst: TableSwitchInst) = buildList<Instruction> {
        val switchMethod = collectorClass.getMethod(
            "blockSwitch",
            MethodDesc(arrayOf(types.stringType, types.objectType), types.voidType)
        )
        val key = run {
            val key = inst.index
            when {
                key.type.isPrimary -> wrapValue(key).also { +it }
                else -> key
            }
        }
        +switchMethod.interfaceCall(
            collectorClass,
            traceCollector,
            arrayOf("${inst.parent.name}".asValue, key)
        )
    }.insertBefore(inst)

    override fun visitThrowInst(inst: ThrowInst) = when {
        inst.parent.parent.isStaticInitializer -> buildList<Instruction> {
            val returnMethod = collectorClass.getMethod("staticExit", MethodDesc(arrayOf(), types.voidType))
            +returnMethod.interfaceCall(collectorClass, traceCollector, arrayOf())
        }
        else -> buildList<Instruction> {
            val throwMethod = collectorClass.getMethod(
                "methodThrow",
                MethodDesc(arrayOf(types.stringType, types.getRefType("java/lang/Throwable")), types.voidType)
            )
            +throwMethod.interfaceCall(
                collectorClass,
                traceCollector,
                arrayOf("${inst.parent.name}".asValue, inst.throwable)
            )
        }
    }.insertBefore(inst)

    override fun visitReturnInst(inst: ReturnInst) = when {
        inst.parent.parent.isStaticInitializer -> buildList<Instruction> {
            val returnMethod = collectorClass.getMethod("staticExit", MethodDesc(arrayOf(), types.voidType))
            +returnMethod.interfaceCall(collectorClass, traceCollector, arrayOf())
        }
        else -> buildList<Instruction> {
            val returnMethod = collectorClass.getMethod(
                "methodReturn",
                MethodDesc(arrayOf(types.stringType), types.voidType)
            )
            +returnMethod.interfaceCall(collectorClass, traceCollector, arrayOf("${inst.parent.name}".asValue))
        }
    }.insertBefore(inst)

    override fun visitCallInst(inst: CallInst) {
        buildList<Instruction> {
            val callMethod = collectorClass.getMethod(
                "methodCall", MethodDesc(
                    arrayOf(
                        types.stringType, types.stringType, types.stringType.asArray,
                        types.stringType, types.stringType, types.stringType, types.stringType.asArray
                    ),
                    types.voidType
                )
            )
            val sizeVal = values.getInt(inst.method.argTypes.size)
            val stringArray = types.stringType.newArray(sizeVal).also { +it }
            val argArray = types.stringType.newArray(sizeVal).also { +it }
            for ((index, arg) in inst.method.argTypes.withIndex()) {
                +stringArray.store(index, arg.asmDesc.asValue)
                +argArray.store(index, inst.args[index].toString().asValue)
            }

            val method = inst.method
            +callMethod.interfaceCall(
                collectorClass,
                traceCollector,
                arrayOf(
                    method.klass.fullName.asValue,
                    method.name.asValue,
                    stringArray,
                    method.returnType.asmDesc.asValue,
                    if (inst.isNameDefined) inst.toString().asValue else values.nullConstant,
                    if (inst.isStatic) values.nullConstant else inst.callee.toString().asValue,
                    argArray
                )
            )
        }.insertBefore(inst)
    }

    override fun visitBasicBlock(bb: BasicBlock) {
        super.visitBasicBlock(bb)
        buildList<Instruction> {
            val entryMethod = collectorClass.getMethod(
                "blockEnter", MethodDesc(
                    arrayOf(types.stringType),
                    types.voidType
                )
            )
            +entryMethod.interfaceCall(
                collectorClass,
                traceCollector,
                arrayOf("${bb.name}".asValue)
            )
        }.insertBefore(bb.first())
    }

    override fun visit(method: Method) {
        if (!method.hasBody) return

        val methodEntryInsts = when {
            method.isStaticInitializer -> buildList {
                traceCollector = getNewCollector()
                +traceCollector
                val entryMethod = collectorClass.getMethod(
                    "staticEntry",
                    MethodDesc(arrayOf(types.stringType), types.voidType)
                )
                +entryMethod.interfaceCall(
                    collectorClass,
                    traceCollector,
                    arrayOf(method.klass.fullName.asValue)
                )
            }
            else -> buildList<Instruction> {
                traceCollector = getNewCollector()
                +traceCollector
                val entryMethod = collectorClass.getMethod(
                    "methodEnter", MethodDesc(
                        arrayOf(
                            types.stringType, types.stringType, types.stringType.asArray,
                            types.stringType, types.objectType, types.objectType.asArray
                        ),
                        types.voidType
                    )
                )
                val sizeVal = method.argTypes.size.asValue
                val stringArray = types.stringType.newArray(sizeVal).also { +it }
                val argArray = types.objectType.newArray(sizeVal).also { +it }
                for ((index, arg) in method.argTypes.withIndex()) {
                    +stringArray.store(index, arg.asmDesc.asValue)
                    val argValue = values.getArgument(index, method, arg).let { argValue ->
                        when {
                            arg.isPrimary -> wrapValue(argValue).also { +it }
                            else -> argValue
                        }
                    }
                    +argArray.store(index, argValue)
                }

                +entryMethod.interfaceCall(
                    collectorClass,
                    traceCollector,
                    arrayOf(
                        method.klass.fullName.asValue,
                        method.name.asValue,
                        stringArray,
                        method.returnType.asmDesc.asValue,
                        if (method.isStatic || method.isConstructor) values.nullConstant else values.getThis(method.klass),
                        argArray
                    )
                )
            }
        }
        super.visit(method)
        methodEntryInsts.insertBefore(method.entry.first())
    }
}