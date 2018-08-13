package org.jetbrains.research.kex

import org.jetbrains.research.kex.asm.state.NoTopologicalSortingError
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.asm.transform.LoopDeroller
import org.jetbrains.research.kex.asm.transform.TraceInstrumenter
import org.jetbrains.research.kex.config.CmdConfig
import org.jetbrains.research.kex.config.FileConfig
import org.jetbrains.research.kex.config.GlobalConfig
import org.jetbrains.research.kex.config.RuntimeConfig
import org.jetbrains.research.kex.runner.CoverageManager
import org.jetbrains.research.kex.runner.CoverageRunner
import org.jetbrains.research.kex.smt.Checker
import org.jetbrains.research.kex.smt.Result
import org.jetbrains.research.kex.util.debug
import org.jetbrains.research.kex.util.error
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kex.util.unreachable
import org.jetbrains.research.kfg.CM
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.analysis.IRVerifier
import org.jetbrains.research.kfg.analysis.LoopAnalysis
import org.jetbrains.research.kfg.analysis.LoopSimplifier
import org.jetbrains.research.kfg.util.Flags
import org.jetbrains.research.kfg.util.JarUtils
import org.jetbrains.research.kfg.util.getClassLoader
import java.io.File
import java.net.URLClassLoader
import java.util.jar.JarFile

fun main(args: Array<String>) {
    val config = GlobalConfig
    val cmd = CmdConfig(args)
    val properties = cmd.getCmdValue("config", "kex.ini")
    config.initialize(cmd, RuntimeConfig, FileConfig(properties))

    val jarName = cmd.getCmdValue("jar")
    val packageName = cmd.getCmdValue("package", "*")
    require(jarName != null, cmd::printHelp)

    val jar = JarFile(jarName)
    val jarLoader = jar.getClassLoader()
    val `package` = Package(packageName.replace('.', '/'))
    CM.parseJar(jar, `package`, Flags.readAll)

    log.debug("Running with jar ${jar.name} and package $`package`")
    val target = File("instrumented/")
    JarUtils.writeClassesToTarget(jar, target, `package`, true) // write all classes to target, so they will be seen by ClassLoader

    val runner = config.getBooleanValue("runner", "use-runner", false)
    if (runner) {
        for (`class` in CM.getConcreteClasses()) {
            for ((_, method) in `class`.methods) {
                val classFileName = "${target.canonicalPath}/${`class`.getFullname()}.class"
                if (!method.isAbstract && method.name != "<init>" && method.name != "<clinit>") {
                    val instrumenter = TraceInstrumenter(method)
                    instrumenter.visit()
                    JarUtils.writeClass(jarLoader, `class`, classFileName)
                    val loader = URLClassLoader(arrayOf(target.toURI().toURL()))
                    CoverageRunner(method, loader).run()
                    instrumenter.insertedInsts.forEach { it.parent?.remove(it) }
                }
                JarUtils.writeClass(jarLoader, `class`, classFileName)
            }
        }
        log.info("Results:")
        val cm = CoverageManager
        for (`class` in CM.getConcreteClasses()) {
            for ((_, method) in `class`.methods) {
                if (!method.isAbstract && !method.isConstructor) {
                    when {
                        cm.isFullCovered(method) -> log.info("\"$method\" full covered")
                        cm.isBodyCovered(method) -> log.info("\"$method\" body covered")
                        else -> log.info("\"$method\" is not covered")
                    }
                }
            }
        }
    }

    for (`class` in CM.getConcreteClasses()) {
        for ((_, method) in `class`.methods) {
            if (method.isAbstract) continue
            val la = LoopAnalysis(method)
            la.visit()
            if (la.loops.isNotEmpty()) {
                val simplifier = LoopSimplifier(method)
                simplifier.visit()
                val deroller = LoopDeroller(method)
                deroller.visit()
            }
            log.debug(method)
            log.debug(method.print())
            IRVerifier(method).visit()

            val psa = try {
                val psa_ = PredicateStateAnalysis(method)
                psa_.visit()
                psa_
            } catch (e: NoTopologicalSortingError) {
                unreachable<PredicateStateAnalysis> { log.error(e) }
            }

            val checker = Checker(method, psa)
            val result = checker.checkReachable(method.last().last())
            log.debug(result)
            if (result is Result.SatResult) log.debug(result.model)
            log.debug()
        }
    }
}