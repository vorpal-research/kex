package org.jetbrains.research.kex

import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.asm.transform.LoopDeroller
import org.jetbrains.research.kex.config.CmdConfig
import org.jetbrains.research.kex.config.GlobalConfig
import org.jetbrains.research.kex.config.FileConfig
import org.jetbrains.research.kex.util.loggerFor
import org.jetbrains.research.kex.asm.transform.TraceInstrumenter
import org.jetbrains.research.kex.runner.CoverageManager
import org.jetbrains.research.kex.runner.CoverageRunner
import org.jetbrains.research.kex.state.transformer.ConstantPropagator
import org.jetbrains.research.kex.state.transformer.StateOptimizer
import org.jetbrains.research.kex.util.debug
import org.jetbrains.research.kex.util.unreachable
import org.jetbrains.research.kfg.CM
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.analysis.IRVerifier
import org.jetbrains.research.kfg.analysis.LoopAnalysis
import org.jetbrains.research.kfg.analysis.LoopSimplifier
import org.jetbrains.research.kfg.util.viewCfg
import org.jetbrains.research.kfg.util.writeClass
import org.jetbrains.research.kfg.util.writeClassesToTarget
import java.io.File
import java.net.URLClassLoader
import java.util.jar.JarFile

fun main(args: Array<String>) {
    val log = loggerFor("org.jetbrains.research.kex.Main")

    val config = GlobalConfig
    val cmd = CmdConfig(args)
    val properties = cmd.getStringValue("properties", "system.properties")
    config.initialize(listOf(cmd, FileConfig(properties)))

    val jarName = config.getStringValue("jar")
    val packageName = config.getStringValue("package", "*")
    assert(jarName != null, cmd::printHelp)

    val jar = JarFile(jarName)
    val `package` = Package(packageName.replace('.', '/'))
    CM.parseJar(jar, `package`)

    log.debug("Running with jar ${jar.name} and package $`package`")
    val target = File("instrumented/")
    writeClassesToTarget(jar, target, `package`, true) // write all classes to target, so they will be seen by ClassLoader

//    for (`class` in CM.getConcreteClasses()) {
//        for ((_, method) in `class`.methods) {
//            val classFileName = "${target.canonicalPath}/${`class`.getFullname()}.class"
//            if (!method.isAbstract() && method.name != "<init>" && method.name != "<clinit>") {
//                val instrumenter = TraceInstrumenter(method)
//                instrumenter.visit()
//                writeClass(`class`, classFileName)
//                val loader = URLClassLoader(arrayOf(target.toURI().toURL()))
//                CoverageRunner(method, loader).run()
//                instrumenter.insertedInsts.forEach { it.parent?.remove(it) }
//            }
//            writeClass(`class`, classFileName)
//        }
//    }
//    log.info("Results:")
//    val cm = CoverageManager
//    for (`class` in CM.getConcreteClasses()) {
//        for ((_, method) in `class`.methods) {
//            if (!method.isAbstract() && method.name != "<init>" && method.name != "<clinit>") {
//                if (cm.isFullCovered(method))
//                    log.info("\"$method\" full covered")
//                else if (cm.isBodyCovered(method))
//                    log.info("\"$method\" body covered")
//                else
//                    log.info("\"$method\" is not covered")
//            }
//        }
//    }

    for (`class` in CM.getConcreteClasses()) {
        for ((_, method) in `class`.methods) {
            val la = LoopAnalysis(method)
            la.visit()
            if (la.loops.isNotEmpty()) {
                val simplifier = LoopSimplifier(method)
                simplifier.visit()
                val deroller = LoopDeroller(method)
                deroller.visit()
            }
            IRVerifier(method).visit()

            val psa = PredicateStateAnalysis(method)
            psa.visit()
            val state = psa.getInstructionState(method.last().last()) ?: continue
            val optimized = StateOptimizer().transform(state)
            log.debug(method)
            log.debug(optimized)
            log.debug("Constant propagator")
            log.debug(ConstantPropagator().transform(optimized))
            log.debug()
        }
    }
}