package org.jetbrains.research.kex.asm.analysis

import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.asm.state.PredicateStateBuilder
import org.jetbrains.research.kex.smt.Checker
import org.jetbrains.research.kex.smt.Result
import org.jetbrains.research.kex.smt.model.ModelRecoverer
import org.jetbrains.research.kex.trace.TraceManager
import org.jetbrains.research.kex.trace.runner.SimpleRunner
import org.jetbrains.research.kex.util.debug
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kex.util.tryOrNull
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.instruction.ReturnInst
import org.jetbrains.research.kfg.visitor.MethodVisitor

val Method.returnInst get() = this.flatten().lastOrNull { it is ReturnInst } as? ReturnInst

class MethodChecker(private val loader: ClassLoader) : MethodVisitor {
    private val tm = TraceManager
    private val psa = PredicateStateAnalysis

    override fun cleanup() {}

    override fun visit(method: Method) {
        super.visit(method)

        if (method.isAbstract || method.isConstructor) return

        log.run {
            debug(method)
            debug(method.print())
            debug()
        }

        val psb = psa.builder(method)

        // check body blocks backwards, to reduce number of runs
        for (block in method.bodyBlocks.reversed()) {
            if (tm.isCovered(block)) continue

            coverBlock(method, block, psb)

            log.debug("Block ${block.name} is covered = ${tm.isCovered(block)}")
            log.debug()
        }
    }

    private fun coverBlock(method: Method, block: BasicBlock, psb: PredicateStateBuilder) {
        val checker = Checker(method, loader, psb)

        log.debug("Checking reachability of ${block.terminator.print()}")
        val result = checker.checkReachable(block.terminator)
        log.debug(result)

        when (result) {
            is Result.SatResult -> {
                log.debug(result.model)
                val recoverer = ModelRecoverer(method, result.model, loader)
                val model = recoverer.apply()
                log.debug("Recovered: ${tryOrNull { model.toString() }}")

                tryOrNull {
                    val trace = SimpleRunner(method, loader).invoke(model.instance, model.arguments.toTypedArray())
                    TraceManager.addTrace(method, trace)
                }
            }
            is Result.UnsatResult -> {
                log.debug("Instruction ${block.terminator.print()} is unreachable")
            }
            is Result.UnknownResult -> Unit
        }
    }
}