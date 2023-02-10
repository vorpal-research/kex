package org.vorpal.research.kex.asm.analysis.symbolic

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.manager.MethodManager
import org.vorpal.research.kex.asm.manager.instantiationManager
import org.vorpal.research.kex.asm.state.asTermExpr
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.ktype.KexRtManager.isKexRt
import org.vorpal.research.kex.ktype.KexRtManager.rtMapped
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.state.term.term
import org.vorpal.research.kex.state.transformer.TermRenamer
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.OuterClass
import org.vorpal.research.kfg.ir.value.instruction.CallInst
import org.vorpal.research.kfg.ir.value.instruction.CallOpcode
import org.vorpal.research.kfg.ir.value.instruction.Handle
import org.vorpal.research.kfg.ir.value.instruction.InvokeDynamicInst
import org.vorpal.research.kfg.stringClass
import org.vorpal.research.kfg.type.ClassType
import ru.spbstu.wheels.mapToArray


interface SymbolicCallResolver {
    fun resolve(state: TraverserState, inst: CallInst): List<Method>
}

interface SymbolicInvokeDynamicResolver {

    fun resolve(state: TraverserState, inst: InvokeDynamicInst): TraverserState?
}

class DefaultCallResolver(
    val ctx: ExecutionContext,
) : SymbolicCallResolver, SymbolicInvokeDynamicResolver {
    private val maximumNumberOfConcreteMethods = kexConfig.getIntValue(
        "symbolic", "numberOfConcreteMethods", 20
    )

    private fun shouldResolve(inst: CallInst): Boolean = when (inst.klass) {
        ctx.cm.stringClass -> false
        MethodManager.KexIntrinsicManager.unknownIntrinsics(ctx.cm) -> false
        MethodManager.KexIntrinsicManager.collectionIntrinsics(ctx.cm) -> false
        MethodManager.KexIntrinsicManager.assertionsIntrinsics(ctx.cm) -> false
        MethodManager.KexIntrinsicManager.objectIntrinsics(ctx.cm) -> false
        else -> true
    }

    override fun resolve(state: TraverserState, inst: CallInst): List<Method> {
        val method = inst.method
        if (!shouldResolve(inst)) return emptyList()
        if (method.klass is OuterClass) return emptyList()
        if (method.isNative) return emptyList()
        if (method.isStatic) return listOf(method.rtMapped)

        val callee = state.mkTerm(inst.callee)
        val baseType = method.klass.rtMapped
        val calleeType = (callee.type.getKfgType(ctx.types) as? ClassType)?.klass ?: return emptyList()
        return when {
            method.isConstructor -> listOf(method.rtMapped)

            calleeType.isKexRt -> listOf(
                calleeType.getMethod(
                    method.name,
                    method.returnType.rtMapped,
                    *method.argTypes.mapToArray { it.rtMapped }
                )
            )

            inst.opcode == CallOpcode.SPECIAL -> listOf(method.rtMapped)
            inst.opcode == CallOpcode.STATIC -> listOf(method.rtMapped)

            callee in state.typeInfo -> {
                val concreteType = state.typeInfo.getValue(callee) as ClassType
                listOf(concreteType.klass.getMethod(method.name, method.desc))

            }

            else -> instantiationManager
                .getAllConcreteSubtypes(baseType, ctx.accessLevel)
                .filter { it.isInheritorOf(calleeType) }
                .mapTo(mutableSetOf()) {
                    when {
                        it != it.rtMapped -> it.rtMapped.getMethod(
                            method.name,
                            method.returnType.rtMapped,
                            *method.argTypes.mapToArray { arg -> arg.rtMapped }
                        )

                        it.isKexRt -> it.getMethod(
                            method.name,
                            method.returnType.rtMapped,
                            *method.argTypes.mapToArray { arg -> arg.rtMapped }
                        )

                        else -> it.getMethod(method.name, method.desc)
                    }
                }
                .filter { it.body.isNotEmpty() }
                .shuffled(ctx.random)
                .take(maximumNumberOfConcreteMethods)
        }.sortedBy { it.prototype }
    }

    override fun resolve(
        state: TraverserState,
        inst: InvokeDynamicInst
    ): TraverserState? {
        val lambdaBases = inst.bootstrapMethodArgs.filterIsInstance<Handle>()
        if (lambdaBases.size != 1) return null

        val lambdaBase = lambdaBases.first()
        val argParameters = lambdaBase.method.argTypes.withIndex().map { term { arg(it.value.kexType, it.index) } }
        val lambdaParameters = lambdaBase.method.argTypes.withIndex().map { (index, type) ->
            term { value(type.kexType, "lambda_${lambdaBase.method.name}_$index") }
        }
        val mapping = argParameters.zip(lambdaParameters).toMap().toMutableMap()
        val `this` = term { `this`(lambdaBase.method.klass.kexType) }
        mapping[`this`] = `this`

        val expr = lambdaBase.method.asTermExpr() ?: return null

        return state.copy(
            valueMap = state.valueMap.put(
                inst,
                term {
                    lambda(inst.type.kexType, lambdaParameters) {
                        TermRenamer("lambda.${lambdaBase.method.name}", mapping)
                            .transform(expr)
                    }
                }
            )
        )
    }
}
