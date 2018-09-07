package org.jetbrains.research.kex.asm.analysis

import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.smt.Checker
import org.jetbrains.research.kex.smt.Result
import org.jetbrains.research.kex.util.debug
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.instruction.ReturnInst
import org.jetbrains.research.kfg.visitor.MethodVisitor

fun Method.returnInst() = this.flatten().lastOrNull { it is ReturnInst } as? ReturnInst

class MethodChecker(val loader: ClassLoader) : MethodVisitor {
    val psa = PredicateStateAnalysis

    override fun cleanup() {}

    override fun visit(method: Method) {
        super.visit(method)

        if (method.isAbstract) return

        log.run {
            debug(method)
            debug(method.print())
            debug()
        }

        val psb = psa.builder(method)
        val checker = Checker(method, psb)

        val returnInst = method.returnInst() ?: return

        val result = checker.checkReachable(returnInst)
        log.debug(result)
        if (result is Result.SatResult) {
            log.debug(result.model)
//            val recoverer = ModelRecoverer(method, result.model, loader)
//            recoverer.apply()
//            log.debug("Recovered: ")
//            for ((term, value) in recoverer.terms) {
//                val memspace = if (term.type is KexPointer) "<${term.memspace}>"  else ""
//                log.debug("$term$memspace = $value")
//            }
        }
        log.debug()
    }
}