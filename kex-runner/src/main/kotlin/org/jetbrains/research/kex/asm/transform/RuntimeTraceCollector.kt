package org.jetbrains.research.kex.asm.transform

import org.jetbrains.research.kthelper.collection.buildList
import org.jetbrains.research.kex.trace.`object`.TraceCollector
import org.jetbrains.research.kex.trace.`object`.TraceCollectorProxy
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.MethodDesc
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.instruction.*
import org.jetbrains.research.kfg.type.ClassType
import org.jetbrains.research.kfg.type.PrimaryType
import org.jetbrains.research.kfg.visitor.MethodVisitor

class RuntimeTraceCollector(override val cm: ClassManager) : MethodVisitor {
    val collectorClass = cm[TraceCollector::class.java.canonicalName.replace('.', '/')]
    lateinit var traceCollector: Instruction

    inline val type get() = cm.type
    inline val instruction get() = cm.instruction
    inline val value get() = cm.value

    private fun Method.isInstrumented(): Boolean {
        val klass = this.`class`
        if (klass !in cm.concreteClasses) return false
        return hasBody
    }

    private fun Value.wrap(): Instruction {
        val wrapperType = this@RuntimeTraceCollector.type.getWrapper(this.type as PrimaryType) as ClassType
        val wrapperClass = wrapperType.`class`
        val valueOfMethod = wrapperClass.getMethod("valueOf", MethodDesc(arrayOf(this.type), wrapperType))
        return instruction.getCall(CallOpcode.Static(), valueOfMethod, wrapperClass, arrayOf(this), true)
    }

    private fun getNewCollector(): Instruction {
        val proxy = cm[TraceCollectorProxy::class.java.canonicalName.replace('.', '/')]
        val getter = proxy.getMethod("currentCollector", MethodDesc(arrayOf(), cm.type.getRefType(collectorClass)))

        return cm.instruction.getCall(CallOpcode.Static(), "collector", getter, proxy, arrayOf())
    }

    private fun List<Instruction>.insertBefore(inst: Instruction) {
        inst.parent.insertBefore(inst, *this.toTypedArray())
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
                            +wrap
                            wrap
                        }
                        else -> cmp.lhv
                    }
                    val rhv = when {
                        cmp.rhv.type.isPrimary -> {
                            val wrap = cmp.rhv.wrap()
                            +wrap
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
                    arrayOf(value.getStringConstant("${inst.parent.name}"), condition, expected), false)
        }
        inst.parent.insertBefore(inst, *blockExitInsts.toTypedArray())
    }

    override fun visitJumpInst(inst: JumpInst) {
        val blockExitInsts = buildList<Instruction> {
            val jumpMethod = collectorClass.getMethod("blockJump",
                    MethodDesc(arrayOf(type.stringType), type.voidType))
            +instruction.getCall(CallOpcode.Virtual(), jumpMethod, collectorClass, traceCollector,
                    arrayOf(value.getStringConstant("${inst.parent.name}")), false)
        }
        inst.parent.insertBefore(inst, *blockExitInsts.toTypedArray())
    }

    override fun visitSwitchInst(inst: SwitchInst) = buildList<Instruction> {
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
                arrayOf(value.getStringConstant("${inst.parent.name}"), key), false)
    }.insertBefore(inst)

    override fun visitTableSwitchInst(inst: TableSwitchInst) = buildList<Instruction> {
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
                arrayOf(value.getStringConstant("${inst.parent.name}"), key), false)
    }.insertBefore(inst)

    override fun visitThrowInst(inst: ThrowInst) = when {
        inst.parent.parent.isStaticInitializer -> buildList<Instruction> {
            val returnMethod = collectorClass.getMethod("staticExit", MethodDesc(arrayOf(), type.voidType))
            +instruction.getCall(CallOpcode.Virtual(), returnMethod, collectorClass, traceCollector, arrayOf(), false)
        }
        else -> buildList<Instruction> {
            val throwMethod = collectorClass.getMethod("methodThrow",
                    MethodDesc(arrayOf(type.stringType, type.getRefType("java/lang/Throwable")), type.voidType))
            +instruction.getCall(CallOpcode.Virtual(), throwMethod, collectorClass, traceCollector,
                    arrayOf(value.getStringConstant("${inst.parent.name}"), inst.throwable), false)
        }
    }.insertBefore(inst)

    override fun visitReturnInst(inst: ReturnInst) = when {
        inst.parent.parent.isStaticInitializer -> buildList<Instruction> {
            val returnMethod = collectorClass.getMethod("staticExit", MethodDesc(arrayOf(), type.voidType))
            +instruction.getCall(CallOpcode.Virtual(), returnMethod, collectorClass, traceCollector, arrayOf(), false)
        }
        else -> buildList<Instruction> {
            val returnMethod = collectorClass.getMethod("methodReturn", MethodDesc(arrayOf(type.stringType), type.voidType))
            +instruction.getCall(CallOpcode.Virtual(), returnMethod, collectorClass, traceCollector,
                    arrayOf(value.getStringConstant("${inst.parent.name}")), false)
        }
    }.insertBefore(inst)

    override fun visitCallInst(inst: CallInst) {
        buildList<Instruction> {
            val callMethod = collectorClass.getMethod("methodCall", MethodDesc(
                    arrayOf(type.stringType, type.stringType, type.getArrayType(type.stringType),
                            type.stringType, type.stringType, type.stringType, type.getArrayType(type.stringType)),
                    type.voidType
            ))
            val sizeVal = value.getIntConstant(inst.method.argTypes.size)
            val stringArray = instruction.getNewArray(type.stringType, sizeVal)
            val argArray = instruction.getNewArray(type.stringType, sizeVal)
            +stringArray
            +argArray
            for ((index, arg) in inst.method.argTypes.withIndex()) {
                val indexVal = value.getIntConstant(index)
                +instruction.getArrayStore(stringArray, indexVal, value.getStringConstant(arg.asmDesc))
                +instruction.getArrayStore(argArray, indexVal, value.getStringConstant(inst.args[index].toString()))
            }

            +instruction.getCall(CallOpcode.Virtual(), callMethod, collectorClass, traceCollector,
                    arrayOf(
                            value.getStringConstant(inst.method.`class`.fullname),
                            value.getStringConstant(inst.method.name),
                            stringArray,
                            value.getStringConstant(inst.method.returnType.asmDesc),
                            if (inst.isNameDefined) value.getStringConstant(inst.toString()) else value.getNullConstant(),
                            if (inst.isStatic) value.getNullConstant() else value.getStringConstant(inst.callee.toString()),
                            argArray
                    ),
                    false)
        }.insertBefore(inst)
    }

    override fun visitBasicBlock(bb: BasicBlock) {
        super.visitBasicBlock(bb)
        buildList<Instruction> {
            val entryMethod = collectorClass.getMethod("blockEnter", MethodDesc(
                    arrayOf(type.stringType),
                    type.voidType
            ))
            +instruction.getCall(CallOpcode.Virtual(), entryMethod, collectorClass, traceCollector,
                    arrayOf(value.getStringConstant("${bb.name}")), false)
        }.insertBefore(bb.first())
    }

    override fun visit(method: Method) {
//        if (method.isStaticInitializer) return
        if (!method.hasBody) return

        val methodEntryInsts = when {
            method.isStaticInitializer -> buildList {
                traceCollector = getNewCollector()
                +traceCollector
                val entryMethod = collectorClass.getMethod("staticEntry", MethodDesc(arrayOf(type.stringType), type.voidType))
                +instruction.getCall(CallOpcode.Virtual(), entryMethod, collectorClass,
                        traceCollector, arrayOf(value.getStringConstant(method.`class`.fullname)), false)
            }
            else -> buildList<Instruction> {
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
                                if (method.isStatic || method.isConstructor) value.getNullConstant() else value.getThis(method.`class`),
                                argArray
                        ),
                        false)
            }
        }
        super.visit(method)
        methodEntryInsts.insertBefore(method.entry.first())
    }
}