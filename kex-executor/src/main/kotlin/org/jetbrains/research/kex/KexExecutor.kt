package org.jetbrains.research.kex

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.jetbrains.research.kex.config.ExecutorCmdConfig
import org.jetbrains.research.kex.config.FileConfig
import org.jetbrains.research.kex.config.RuntimeConfig
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.descriptor.descriptor
import org.jetbrains.research.kex.random.easyrandom.EasyRandomDriver
import org.jetbrains.research.kex.serialization.KexSerializer
import org.jetbrains.research.kex.trace.symbolic.ExceptionResult
import org.jetbrains.research.kex.trace.symbolic.SuccessResult
import org.jetbrains.research.kex.trace.symbolic.TraceCollectorProxy
import org.jetbrains.research.kex.util.getPathSeparator
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.KfgConfig
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.container.Container
import org.jetbrains.research.kfg.container.asContainer
import org.jetbrains.research.kfg.ir.value.NameMapperContext
import org.jetbrains.research.kfg.util.Flags
import org.jetbrains.research.kthelper.logging.log
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
    private val target = Package.parse(cmd.getCmdValue("package")!!)

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
        val ctx = ExecutionContext(
            classManager,
            target,
            containerClassLoader,
            EasyRandomDriver(),
            containers.map { it.path }
        )
        val serializer = KexSerializer(ctx.cm)

        val klass = cmd.getCmdValue("class")!!
        val setupMethod = cmd.getCmdValue("setup")!!
        val testMethod = cmd.getCmdValue("test")!!

        val javaClass = Class.forName(klass)
        val instance = javaClass.newInstance()

        try {
            val setup = javaClass.getMethod(setupMethod)
            setup.invoke(instance)
        } catch (e: Throwable) {
            exitProcess(1)
        }

        val collector = TraceCollectorProxy.enableCollector(ctx, NameMapperContext())
        var exception: Throwable? = null
        try {
            val test = javaClass.getMethod(testMethod)
            test.invoke(instance)
        } catch (e: Throwable) {
            exception = e
        } finally {
            TraceCollectorProxy.disableCollector()
            log.debug("Collected state: ${collector.symbolicState}")
            val result = when {
                exception != null -> ExceptionResult(exception.descriptor, collector.symbolicState)
                else -> SuccessResult(collector.symbolicState)
            }
            val jsonString = serializer.toJson(result)
            output.toFile().writeText(jsonString)
        }
    }
}