package org.jetbrains.research.kex.asm.analysis.libchecker

import org.jetbrains.research.kex.util.apply
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.*
import org.jetbrains.research.kfg.ir.value.instruction.CallOpcode
import org.jetbrains.research.kfg.ir.value.usageContext
import org.jetbrains.research.kfg.visitor.ClassVisitor
import org.jetbrains.research.libsl.asg.Function
import org.jetbrains.research.libsl.asg.LslContext

class UnvisitedLslMethodsVisitor(
    override val cm: ClassManager,
    private val lslContext: LslContext
    ) : ClassVisitor {
    override fun cleanup() { }

    override fun visit(klass: Class) {
        val automatonName = klass.fullName
        val automaton = lslContext.resolveAutomaton(automatonName.replace("/", ".")) ?: return

        val undefinedLslFuncs = automaton.functions.filter { func ->
            val descriptor = func.desc
            val name = func.name
            klass.allMethods.firstOrNull { it.name == name && it.desc == descriptor } == null
        }

        for (func in undefinedLslFuncs) {
            val method = klass.getMethodConcrete(func.name, func.desc)
                ?: error("class $klass doesn't contain method ${func.name}")

            val usageContext = method.usageContext
            val overrideMethod = klass.addMethod(func.name, method.desc)

            val args = overrideMethod.argTypes.mapIndexed { index, type ->
                cm.value.getArgument(index, method, type)
            }.toTypedArray()
            val entryBlock = BodyBlock("entry")
            val overriddenMethodCallBlock = BodyBlock("methodCall")

            overrideMethod.add(usageContext, entryBlock)
            overrideMethod.add(usageContext, overriddenMethodCallBlock)

            entryBlock.apply {
                add(cm.instruction.getJump(usageContext, overriddenMethodCallBlock))
                addSuccessor(usageContext, overriddenMethodCallBlock)
            }
            val `this` = cm.value.getThis(klass)
            val call = cm.instruction.getCall(usageContext, CallOpcode.SPECIAL, method, klass, `this`, args, isNamed = true)
            overriddenMethodCallBlock.apply {
                add(call)
                addPredecessor(usageContext, entryBlock)
                if (method.returnType != types.voidType) {
                    add(cm.instruction.getReturn(usageContext, call))
                } else {
                    add(cm.instruction.getReturn(usageContext))
                }
            }
        }
    }

    private val Function.desc: MethodDesc
        get() {
            val args = args.map { it.type.kfgType(cm) }.toTypedArray()
            val returnType = returnType?.kfgType(cm) ?: cm.type.voidType
            return MethodDesc(args, returnType)
        }
}