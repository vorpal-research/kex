package org.vorpal.research.kex.smt

import org.vorpal.research.kex.InheritanceInfo
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kfg.type.TypeFactory
import org.vorpal.research.kthelper.assert.fail
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log

private val engine = kexConfig.getStringValue("smt", "engine")
    ?: unreachable { log.error("No SMT engine specified") }

class SMTProxySolver(
    tf: TypeFactory,
    val solver: AbstractSMTSolver = getSolver(tf, engine)
) : AbstractSMTSolver by solver {

    companion object {
        val solvers = run {
            val loader = Thread.currentThread().contextClassLoader
            val resource = loader.getResourceAsStream("solvers.json")
                ?: fail { log.error("Could not load smt solver inheritance info") }
            val inheritanceInfo = InheritanceInfo.fromJson(resource.bufferedReader().readText())
            resource.close()

            inheritanceInfo.inheritors.associate {
                it.name to loader.loadClass(it.inheritorClass)
            }
        }

        fun getSolver(tf: TypeFactory, engine: String): AbstractSMTSolver {
            val solverClass = solvers[engine] ?: unreachable { log.error("Unknown engine name: $engine") }
            val constructor = solverClass.getConstructor(TypeFactory::class.java)
            return constructor.newInstance(tf) as AbstractSMTSolver
        }
    }

    constructor(tf: TypeFactory, engine: String) : this(tf, getSolver(tf, engine))
}

class AsyncSMTProxySolver(
    tf: TypeFactory,
    val solver: AbstractAsyncSMTSolver = getSolver(tf, engine)
) : AbstractAsyncSMTSolver by solver {

    companion object {
        val solvers = run {
            val loader = Thread.currentThread().contextClassLoader
            val resource = loader.getResourceAsStream("async-solvers.json")
                ?: fail { log.error("Could not load smt solver inheritance info") }
            val inheritanceInfo = InheritanceInfo.fromJson(resource.bufferedReader().readText())
            resource.close()

            inheritanceInfo.inheritors.associate {
                it.name to loader.loadClass(it.inheritorClass)
            }
        }

        fun getSolver(tf: TypeFactory, engine: String): AbstractAsyncSMTSolver {
            val solverClass = solvers[engine] ?: unreachable { log.error("Unknown engine name: $engine") }
            val constructor = solverClass.getConstructor(TypeFactory::class.java)
            return constructor.newInstance(tf) as AbstractAsyncSMTSolver
        }
    }

    constructor(tf: TypeFactory, engine: String) : this(tf, getSolver(tf, engine))
}
