package org.vorpal.research.kex

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.vorpal.research.kex.config.ExecutorCmdConfig
import org.vorpal.research.kex.config.FileConfig
import org.vorpal.research.kex.config.RuntimeConfig
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.descriptor.convertToDescriptor
import org.vorpal.research.kex.random.easyrandom.EasyRandomDriver
import org.vorpal.research.kex.serialization.KexSerializer
import org.vorpal.research.kex.trace.symbolic.TraceCollectorProxy
import org.vorpal.research.kex.trace.symbolic.protocol.ExceptionResult
import org.vorpal.research.kex.trace.symbolic.protocol.SuccessResult
import org.vorpal.research.kex.util.PathClassLoader
import org.vorpal.research.kex.util.getIntrinsics
import org.vorpal.research.kex.util.getPathSeparator
import org.vorpal.research.kex.util.getRuntime
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.KfgConfig
import org.vorpal.research.kfg.container.Container
import org.vorpal.research.kfg.container.asContainer
import org.vorpal.research.kfg.ir.value.NameMapperContext
import org.vorpal.research.kfg.util.Flags
import org.vorpal.research.kthelper.logging.error
import org.vorpal.research.kthelper.logging.log
import java.nio.file.Paths
import kotlin.system.exitProcess

@Suppress("DEPRECATION")
@ExperimentalSerializationApi
@InternalSerializationApi
fun main(args: Array<String>) {
    KexExecutor(args).main()
}

@Deprecated(
    message = "replaced with master-worker executor",
    replaceWith = ReplaceWith("org.vorpal.research.kex.launcher.MasterLauncher")
)
class KexExecutor(args: Array<String>) {
    private val cmd = ExecutorCmdConfig(args)
    private val properties = cmd.getCmdValue("config", "kex.ini")
    private val output = cmd.getCmdValue("output")!!.let { Paths.get(it) }

    private val containers: List<Container>
    private val containerClassLoader: PathClassLoader
    private val classManager: ClassManager

    init {
        kexConfig.initialize(cmd, RuntimeConfig, FileConfig(properties))
        val logName = kexConfig.getStringValue("kex", "log", "kex-executor.log")
        kexConfig.initLog(logName)

        val classPaths = cmd.getCmdValue("classpath")!!
            .split(getPathSeparator())
            .map { Paths.get(it).toAbsolutePath() }
        containerClassLoader = PathClassLoader(classPaths)

        containers = classPaths.map {
            it.asContainer() ?: run {
                log.error("Can't represent ${it.toAbsolutePath()} as class container")
                exitProcess(1)
            }
        }
        classManager = ClassManager(KfgConfig(flags = Flags.readAll, failOnError = false, verifyIR = false))
        classManager.initialize(
            *listOfNotNull(
                *containers.toTypedArray(),
                getRuntime(),
                getIntrinsics()
            ).toTypedArray()
        )
    }

    @ExperimentalSerializationApi
    @InternalSerializationApi
    fun main() {
        val ctx = ExecutionContext(
            classManager,
            containerClassLoader,
            EasyRandomDriver(),
            containers.map { it.path }
        )
        val serializer = KexSerializer(ctx.cm)

        val klass = cmd.getCmdValue("class")!!
        val setupMethod = cmd.getCmdValue("setup")!!
        val testMethod = cmd.getCmdValue("test")!!

        val javaClass = Class.forName(klass)
        val instance = javaClass.getConstructor().newInstance()

        try {
            val setup = javaClass.getMethod(setupMethod)
            setup.invoke(instance)
        } catch (e: Throwable) {
            log.error(e)
            e.printStackTrace(System.err)
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
            log.debug("Collected state: {}", collector.symbolicState)
            val result = when {
                exception != null -> ExceptionResult(
                    convertToDescriptor(exception),
                    collector.instructionTrace,
                    collector.symbolicState
                )

                else -> SuccessResult(collector.instructionTrace, collector.symbolicState)
            }
            val jsonString = serializer.toJson(result)
            output.toFile().writeText(jsonString)
        }
    }
}
