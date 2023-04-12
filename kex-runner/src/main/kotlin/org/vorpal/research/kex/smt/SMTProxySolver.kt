@file:Suppress("unused")

package org.vorpal.research.kex.smt

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.InheritanceInfo
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kthelper.assert.fail
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log

private val engine = kexConfig.getStringValue("smt", "engine")
    ?: unreachable { log.error("No SMT engine specified") }

class SMTProxySolver(
    ctx: ExecutionContext,
    private val solver: AbstractSMTSolver = getSolver(ctx, engine)
) : AbstractSMTSolver by solver {

    companion object {
        private val solvers = run {
            val loader = Thread.currentThread().contextClassLoader
            val resource = loader.getResourceAsStream("solvers.json")
                ?: fail { log.error("Could not load smt solver inheritance info") }
            val inheritanceInfo = InheritanceInfo.fromJson(resource.bufferedReader().readText())
            resource.close()

            inheritanceInfo.inheritors.associate {
                it.name to loader.loadClass(it.inheritorClass)
            }
        }

        fun getSolver(ctx: ExecutionContext, engine: String): AbstractSMTSolver {
            val solverClass = solvers[engine] ?: unreachable { log.error("Unknown engine name: $engine") }
            val constructor = solverClass.getConstructor(ExecutionContext::class.java)
            return constructor.newInstance(ctx) as AbstractSMTSolver
        }
    }

    constructor(ctx: ExecutionContext, engine: String) : this(ctx, getSolver(ctx, engine))
}

class AsyncSMTProxySolver(
    ctx: ExecutionContext,
    private val solver: AbstractAsyncSMTSolver = getSolver(ctx, engine)
) : AbstractAsyncSMTSolver by solver {

    companion object {
        private val solvers = run {
            val loader = Thread.currentThread().contextClassLoader
            val resource = loader.getResourceAsStream("async-solvers.json")
                ?: fail { log.error("Could not load smt solver inheritance info") }
            val inheritanceInfo = InheritanceInfo.fromJson(resource.bufferedReader().readText())
            resource.close()

            inheritanceInfo.inheritors.associate {
                it.name to loader.loadClass(it.inheritorClass)
            }
        }

        fun getSolver(ctx: ExecutionContext, engine: String): AbstractAsyncSMTSolver {
            val solverClass = solvers[engine] ?: unreachable { log.error("Unknown engine name: $engine") }
            val constructor = solverClass.getConstructor(ExecutionContext::class.java)
            return constructor.newInstance(ctx) as AbstractAsyncSMTSolver
        }
    }

    constructor(ctx: ExecutionContext, engine: String) : this(ctx, getSolver(ctx, engine))
}
