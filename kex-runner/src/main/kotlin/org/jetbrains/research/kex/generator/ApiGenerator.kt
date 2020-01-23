package org.jetbrains.research.kex.generator

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.smt.DescriptorReanimator
import org.jetbrains.research.kex.smt.Reanimator
import org.jetbrains.research.kex.smt.SMTModel
import org.jetbrains.research.kex.state.PredicateState
import org.jetbrains.research.kfg.ir.Method

class ApiGenerator(method: Method,
                   ctx: ExecutionContext,
                   model: SMTModel) : AbstractGenerator<Descriptor>(method, ctx, model) {
    override val reanimator: Reanimator<Descriptor> = DescriptorReanimator(method, model, ctx)
}

fun generateDescriptors(method: Method, ctx: ExecutionContext, model: SMTModel, state: PredicateState) =
        ApiGenerator(method, ctx, model).generate(state).let { it.first to it.second.map { arg -> arg!! } }