package org.jetbrains.research.kex.smt

import org.jetbrains.research.kthelper.assert.fail
import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log
import org.jetbrains.research.kex.InheritanceInfo
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kfg.type.TypeFactory

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

            inheritanceInfo?.inheritors?.map {
                it.name to loader.loadClass(it.inheritorClass)
            }?.toMap() ?: mapOf()
        }

        fun getSolver(tf: TypeFactory, engine: String): AbstractSMTSolver {
            val solverClass = solvers[engine] ?: unreachable { log.error("Unknown engine name: $engine") }
            val constructor = solverClass.getConstructor(TypeFactory::class.java)
            return constructor.newInstance(tf) as AbstractSMTSolver
        }
    }

    constructor(tf: TypeFactory, engine: String) : this(tf, getSolver(tf, engine))
}