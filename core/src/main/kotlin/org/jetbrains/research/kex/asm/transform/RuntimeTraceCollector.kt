package org.jetbrains.research.kex.asm.transform

import org.jetbrains.research.kex.trace.runtime.TraceCollector
import org.jetbrains.research.kex.trace.runtime.TraceCollectorProxy
import org.jetbrains.research.kex.util.buildList
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.MethodDesc
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.instruction.*
import org.jetbrains.research.kfg.type.ClassType
import org.jetbrains.research.kfg.visitor.MethodVisitor

class RuntimeTraceCollector(override val cm: ClassManager) : MethodVisitor {
    val collectorClass = cm.getByName(TraceCollector::class.java.canonicalName.replace('.', '/'))
    lateinit var traceCollector: Instruction

    inline val type get() = cm.type
    inline val instruction get() = cm.instruction
    inline val value get() = cm.value

    private fun Value.wrap(): Instruction {
        val wrapperType = this@RuntimeTraceCollector.type.getWrapper(this.type) as ClassType
        val wrapperClass = wrapperType.`class`
        val valueOfMethod = wrapperClass.getMethod("valueOf", MethodDesc(arrayOf(this.type), wrapperType))
        return instruction.getCall(CallOpcode.Static(), valueOfMethod, wrapperClass, arrayOf(this), true)
    }

    private fun getNewCollector(): Instruction {
        val proxy = cm.getByName(TraceCollectorProxy::class.java.canonicalName.replace('.', '/'))
        val getter = proxy.getMethod("currentCollector", MethodDesc(arrayOf(), cm.type.getRefType(collectorClass)))

        return cm.instruction.getCall(CallOpcode.Static(), "collector", getter, proxy, arrayOf())
    }

    override fun cleanup() {}

    override fun visitBranchInst(inst: BranchInst) {
        val blockExitInsts = buildList<Instruction> {
            val branchMethod = collectorClass.getMethod("blockBranch",
                    MethodDesc(arrayOf(type.stringType, type.objectType, type.objectType), type.voidType))
            val (condition, expected) = when (inst.cond) {
                is CmpInst -> {
                    val cmp = inst.cond as CmpInst
                    val lhv = when {
                        cmp.lhv.type.isPrimary -> {
                            val wrap = cmp.lhv.wrap()
                            + wrap
                            wrap
                        }
                        else -> cmp.lhv
                    }
                    val rhv = when {
                        cmp.rhv.type.isPrimary -> {
                            val wrap = cmp.rhv.wrap()
                            + wrap
                            wrap
                        }
                        else -> cmp.rhv
                    }
                    lhv to rhv
                }
                else -> {
                    val wrap = inst.cond.wrap()
                    +wrap
                    wrap to value.getNullConstant()
                }
            }
            +instruction.getCall(CallOpcode.Virtual(), branchMethod, collectorClass, traceCollector,
                    arrayOf(value.getStringConstant("${inst.parent!!.name}"), condition, expected), false)
        }
        inst.parent!!.insertBefore(inst, *blockExitInsts.toTypedArray())
    }

    override fun visitJumpInst(inst: JumpInst) {
        val blockExitInsts = buildList<Instruction> {
            val jumpMethod = collectorClass.getMethod("blockJump",
                    MethodDesc(arrayOf(type.stringType), type.voidType))
            +instruction.getCall(CallOpcode.Virtual(), jumpMethod, collectorClass, traceCollector,
                    arrayOf(value.getStringConstant("${inst.parent!!.name}")), false)
        }
        inst.parent!!.insertBefore(inst, *blockExitInsts.toTypedArray())
    }

    override fun visitSwitchInst(inst: SwitchInst) {
        val blockExitInsts = buildList<Instruction> {
            val switchMethod = collectorClass.getMethod("blockSwitch",
                    MethodDesc(arrayOf(type.stringType, type.objectType), type.voidType))
            val key = run {
                val key = inst.key
                when {
                    key.type.isPrimary -> {
                        val wrap = key.wrap()
                        +wrap
                        wrap
                    }
                    else -> key
                }
            }
            +instruction.getCall(CallOpcode.Virtual(), switchMethod, collectorClass, traceCollector,
                    arrayOf(value.getStringConstant("${inst.parent!!.name}"), key), false)
        }
        inst.parent!!.insertBefore(inst, *blockExitInsts.toTypedArray())
    }

    override fun visitTableSwitchInst(inst: TableSwitchInst) {
        val blockExitInsts = buildList<Instruction> {
            val switchMethod = collectorClass.getMethod("blockSwitch",
                    MethodDesc(arrayOf(type.stringType, type.objectType), type.voidType))
            val key = run {
                val key = inst.index
                when {
                    key.type.isPrimary -> {
                        val wrap = key.wrap()
                        +wrap
                        wrap
                    }
                    else -> key
                }
            }
            +instruction.getCall(CallOpcode.Virtual(), switchMethod, collectorClass, traceCollector,
                    arrayOf(value.getStringConstant("${inst.parent!!.name}"), key), false)
        }
        inst.parent!!.insertBefore(inst, *blockExitInsts.toTypedArray())
    }

    override fun visitThrowInst(inst: ThrowInst) {
        val methodExitInsts = buildList<Instruction> {
            val throwMethod = collectorClass.getMethod("methodThrow",
                    MethodDesc(arrayOf(type.getRefType("java/lang/Throwable")), type.voidType))
            +instruction.getCall(CallOpcode.Virtual(), throwMethod, collectorClass, traceCollector, arrayOf(inst.throwable), false)
        }
        inst.parent!!.insertBefore(inst, *methodExitInsts.toTypedArray())
    }

    override fun visitReturnInst(inst: ReturnInst) {
        val methodExitInsts = buildList<Instruction> {
            val returnMethod = collectorClass.getMethod("methodReturn", MethodDesc(arrayOf(), type.voidType))
            +instruction.getCall(CallOpcode.Virtual(), returnMethod, collectorClass, traceCollector, arrayOf(), false)
        }
        inst.parent!!.insertBefore(inst, *methodExitInsts.toTypedArray())
    }

    override fun visitBasicBlock(bb: BasicBlock) {
        val blockEntryInsts = buildList<Instruction> {
            val entryMethod = collectorClass.getMethod("blockEnter", MethodDesc(
                    arrayOf(type.stringType),
                    type.voidType
            ))
            +instruction.getCall(CallOpcode.Virtual(), entryMethod, collectorClass, traceCollector,
                    arrayOf(value.getStringConstant("${bb.name}")), false)
        }
        bb.insertBefore(bb.first(), *blockEntryInsts.toTypedArray())
        super.visitBasicBlock(bb)
    }

    override fun visit(method: Method) {
        if (method.isConstructor) return
        val methodEntryInsts = buildList<Instruction> {
            traceCollector = getNewCollector()
            +traceCollector
            val entryMethod = collectorClass.getMethod("methodEnter", MethodDesc(
                    arrayOf(type.stringType, type.stringType, type.getArrayType(type.stringType),
                            type.stringType, type.objectType, type.getArrayType(type.objectType)),
                    type.voidType
            ))
            val sizeVal = value.getIntConstant(method.argTypes.size)
            val stringArray = instruction.getNewArray(type.stringType, sizeVal)
            val argArray = instruction.getNewArray(type.objectType, sizeVal)
            +stringArray
            +argArray
            for ((index, arg) in method.argTypes.withIndex()) {
                val indexVal = value.getIntConstant(index)
                +instruction.getArrayStore(stringArray, indexVal, value.getStringConstant(arg.asmDesc))
                +when {
                    arg.isPrimary -> {
                        val wrapped = value.getArgument(index, method, arg).wrap()
                        +wrapped
                        instruction.getArrayStore(argArray, indexVal, wrapped)
                    }
                    else -> instruction.getArrayStore(argArray, indexVal, value.getArgument(index, method, arg))
                }
            }

            +instruction.getCall(CallOpcode.Virtual(), entryMethod, collectorClass, traceCollector,
                    arrayOf(
                            value.getStringConstant(method.`class`.fullname),
                            value.getStringConstant(method.name),
                            stringArray,
                            value.getStringConstant(method.returnType.asmDesc),
                            if (method.isStatic) value.getNullConstant() else value.getThis(method.`class`),
                            argArray
                    ),
                    false)
        }
        super.visit(method)
        method.entry.insertBefore(method.entry.first(), *methodEntryInsts.toTypedArray())
    }
}