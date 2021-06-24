package org.jetbrains.research.kex

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.jetbrains.research.kex.config.ExecutorCmdConfig
import org.jetbrains.research.kex.config.FileConfig
import org.jetbrains.research.kex.config.RuntimeConfig
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.random.easyrandom.EasyRandomDriver
import org.jetbrains.research.kex.serialization.KexSerializer
import org.jetbrains.research.kex.trace.symbolic.TraceCollectorProxy
import org.jetbrains.research.kex.util.getPathSeparator
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.KfgConfig
import org.jetbrains.research.kfg.container.Container
import org.jetbrains.research.kfg.container.asContainer
import org.jetbrains.research.kfg.util.Flags
import org.jetbrains.research.kthelper.logging.log
import org.jetbrains.research.kfg.Package
import java.net.URLClassLoader
import java.nio.file.Paths
import kotlin.system.exitProcess

@ExperimentalSerializationApi
@InternalSerializationApi
fun main(args: Array<String>) {
    KexExecutor(args).main()
}

class KexExecutor(args: Array<String>) {
    private val cmd = ExecutorCmdConfig(args)
    private val properties = cmd.getCmdValue("config", "kex.ini")
    private val output = cmd.getCmdValue("output")!!.let { Paths.get(it) }

    val containers: List<Container>
    val containerClassLoader: URLClassLoader
    val classManager: ClassManager

    init {
        kexConfig.initialize(cmd, RuntimeConfig, FileConfig(properties))
        val logName = kexConfig.getStringValue("kex", "log", "kex-executor.log")
        kexConfig.initLog(logName)

        val classPaths = cmd.getCmdValue("classpath")!!
            .split(getPathSeparator())
            .map { Paths.get(it).toAbsolutePath() }
        containerClassLoader = URLClassLoader(*classPaths.map { it.toUri().toURL() }.toTypedArray())
        val target = Package.parse(cmd.getCmdValue("package")!!)

        containers = classPaths.map {
            it.asContainer(target) ?: run {
                log.error("Can't represent ${it.toAbsolutePath()} as class container")
                exitProcess(1)
            }
        }
        classManager = ClassManager(KfgConfig(flags = Flags.readAll, failOnError = false, verifyIR = false))
        classManager.initialize(*containers.toTypedArray())
    }

    @ExperimentalSerializationApi
    @InternalSerializationApi
    fun main() {
        val ctx = ExecutionContext(classManager, containerClassLoader, EasyRandomDriver())

        val klass = cmd.getCmdValue("class")!!
        val setupMethod = cmd.getCmdValue("setup")!!
        val testMethod = cmd.getCmdValue("test")!!

        val loader = this.javaClass.classLoader
        val javaClass = loader.loadClass(klass)
        val instance = javaClass.newInstance()

        try {
            val setup = javaClass.getMethod(setupMethod)
            setup.invoke(instance)
        } catch (e: Throwable) {
            log.error("Could not initialize test")
        }

        val collector = TraceCollectorProxy.enableCollector(ctx)
        try {
            val test = javaClass.getMethod(testMethod)
            test.invoke(instance)
        } finally {
            TraceCollectorProxy.disableCollector()
            val jsonString = KexSerializer(ctx.cm).toJson(collector.symbolicState)
            output.toFile().writeText(jsonString)
        }
    }
}