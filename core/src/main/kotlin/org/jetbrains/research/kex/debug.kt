package org.jetbrains.research.kex

import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.UnstableDefault
import org.jetbrains.research.kex.asm.analysis.Failure
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.config.CmdConfig
import org.jetbrains.research.kex.config.FileConfig
import org.jetbrains.research.kex.config.RuntimeConfig
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.serialization.KexSerializer
import org.jetbrains.research.kex.smt.Checker
import org.jetbrains.research.kex.smt.Result
import org.jetbrains.research.kex.state.transformer.executeModel
import org.jetbrains.research.kex.util.debug
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.util.Flags
import org.jetbrains.research.kfg.util.classLoader
import java.io.File
import java.util.jar.JarFile

@UnstableDefault
@ImplicitReflectionSerializer
fun main(args: Array<String>) {
    val cmd = CmdConfig(args)
    val properties = cmd.getCmdValue("config", "kex.ini")
    kexConfig.initialize(cmd, RuntimeConfig, FileConfig(properties))
    kexConfig.initLog("kex-debug.log")

    val jarName = cmd.getCmdValue("jar")
    val packageName = cmd.getCmdValue("package", "*")
    require(jarName != null, cmd::printHelp)

    val jar = JarFile(jarName)
    val jarLoader = jar.classLoader
    val `package` = Package(packageName.replace('.', '/'))
    val classManager = ClassManager(jar, `package`, Flags.readAll)
    val psa = PredicateStateAnalysis(classManager)

    val psFile = cmd.getCmdValue("ps") ?: throw IllegalArgumentException("Specify PS file to debug")
    val failure = KexSerializer(classManager).fromJson<Failure>(File(psFile).readText())

    val method = failure.method
    log.debug(failure)

    val checker = Checker(method, jarLoader, psa)
    val result = checker.check(failure.state) as? Result.SatResult ?: return
    log.debug(result.model)
    val recMod = executeModel(checker.state, classManager.type, method, result.model, jarLoader)
    log.debug(recMod)
}