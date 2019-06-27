package org.jetbrains.research.kex

import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.UnstableDefault
import org.jetbrains.research.kex.asm.analysis.PSWithMessage
import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.config.CmdConfig
import org.jetbrains.research.kex.config.FileConfig
import org.jetbrains.research.kex.config.RuntimeConfig
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.serialization.KexSerializer
import org.jetbrains.research.kex.smt.Checker
import org.jetbrains.research.kex.smt.Result
import org.jetbrains.research.kex.smt.model.ModelRecoverer
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

    val jarName = cmd.getCmdValue("jar")
    val packageName = cmd.getCmdValue("package", "*")
    require(jarName != null, cmd::printHelp)

    val jar = JarFile(jarName)
    val jarLoader = jar.classLoader
    val `package` = Package(packageName.replace('.', '/'))
    val classManager = ClassManager(jar, `package`, Flags.readAll)
    val psa = PredicateStateAnalysis(classManager)

    val psFile = cmd.getCmdValue("ps") ?: throw IllegalArgumentException("Specify PS file to debug")
    val psWithMessage = KexSerializer(classManager).fromJson<PSWithMessage>(File(psFile).readText())

    val klass = classManager.getByName("org/jetbrains/research/kex/test/debug/ArrayLongTests")
    val method = klass.getMethod("testUnknownArrayWrite", "([I)V")

    log.debug(psWithMessage)

    val checker = Checker(method, jarLoader, psa)
    val result = checker.check(psWithMessage.state) as? Result.SatResult ?: return
    log.debug(result.model)
    ModelRecoverer(method, result.model, jarLoader).apply()
}