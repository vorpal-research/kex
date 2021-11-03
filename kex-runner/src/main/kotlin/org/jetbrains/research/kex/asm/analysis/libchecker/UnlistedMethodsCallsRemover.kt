package org.jetbrains.research.kex.asm.analysis.libchecker

import org.jetbrains.research.kex.state.predicate.CallPredicate
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.term.CallTerm
import org.jetbrains.research.kex.state.transformer.Transformer
import org.jetbrains.research.kex.state.transformer.isThis
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kthelper.logging.log

class UnlistedMethodsCallsRemover(
    private val allowedMethods: Collection<Method>
) : Transformer<UnlistedMethodsCallsRemover> {
    override fun transformCall(predicate: CallPredicate): Predicate {
        val callTerm = predicate.callTerm as? CallTerm  ?: return nothing()
        val method = callTerm.method
        if (method in allowedMethods || callTerm.owner.isThis) {
            return predicate
        }
        log.info("drop call predicate: $predicate")
        return nothing()
    }
}