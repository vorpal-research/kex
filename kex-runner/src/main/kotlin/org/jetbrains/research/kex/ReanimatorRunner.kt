package org.jetbrains.research.kex

import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.asm.transform.LoopDeroller
import org.jetbrains.research.kex.asm.util.Visibility
import org.jetbrains.research.kex.config.FileConfig
import org.jetbrains.research.kex.config.RuntimeConfig
import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.descriptor.*
import org.jetbrains.research.kex.ktype.*
import org.jetbrains.research.kex.parameters.Parameters
import org.jetbrains.research.kex.random.easyrandom.EasyRandomDriver
import org.jetbrains.research.kex.reanimator.callstack.CallStack
import org.jetbrains.research.kex.reanimator.callstack.generator.CallStackGenerator
import org.jetbrains.research.kex.reanimator.callstack.generator.GeneratorContext
import org.jetbrains.research.kex.reanimator.codegen.JUnitTestCasePrinter
import org.jetbrains.research.kex.reanimator.collector.ExternalCtorCollector
import org.jetbrains.research.kex.reanimator.collector.MethodFieldAccessCollector
import org.jetbrains.research.kex.reanimator.collector.SetterCollector
import org.jetbrains.research.kex.util.getRuntime
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.KfgConfig
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.analysis.LoopSimplifier
import org.jetbrains.research.kfg.container.JarContainer
import org.jetbrains.research.kfg.util.Flags
import org.jetbrains.research.kfg.visitor.executePipeline
import java.net.URLClassLoader
import java.nio.file.Path
import org.jetbrains.research.descriptor.ArrayDescriptor as JavaArray
import org.jetbrains.research.descriptor.ConstantDescriptor as JavaConstant
import org.jetbrains.research.descriptor.Descriptor as JavaDescriptor
import org.jetbrains.research.descriptor.NullDescriptor as JavaNull
import org.jetbrains.research.descriptor.ObjectDescriptor as JavaObject

class ReanimatorRunner(
    val config: Path,
    val target: Path,
    val pkg: Package
) {
    val cm: ClassManager
    val context: ExecutionContext
    val visibilityLevel: Visibility
    val generatorContext: GeneratorContext

    init {
        kexConfig.initialize(RuntimeConfig, FileConfig(config.toString()))
        kexConfig.initLog("kex.log")

        val jar = JarContainer(target, pkg)
        cm = ClassManager(KfgConfig(flags = Flags.readAll, failOnError = false))
        val analysisJars = listOfNotNull(jar, getRuntime())
        cm.initialize(*analysisJars.toTypedArray())

        context = ExecutionContext(cm, pkg, jar.classLoader, EasyRandomDriver(), analysisJars.map { it.path })
        updateClassPath(jar.classLoader)
        val psa = PredicateStateAnalysis(context.cm)

        visibilityLevel = kexConfig.getEnumValue("apiGeneration", "visibility", true, Visibility.PUBLIC)

        executePipeline(cm, Package.defaultPackage) {
            +LoopSimplifier(cm)
            +LoopDeroller(cm)
            +psa
            +MethodFieldAccessCollector(context, psa)
            +SetterCollector(context)
            +ExternalCtorCollector(cm, visibilityLevel)
        }

        generatorContext = GeneratorContext(context, psa, visibilityLevel)

    }

    private fun updateClassPath(loader: URLClassLoader) {
        val urlClassPath = loader.urLs.joinToString(separator = ":") { "${it.path}." }
        val classPath = System.getProperty("java.class.path")
        System.setProperty("java.class.path", "$classPath:$urlClassPath")
    }

    fun print(
        testClassName: String,
        testMethodName: String,
        klass: String,
        method: String,
        desc: String,
        instance: CallStack,
        args: List<CallStack>
    ): String {
        val kfgKlass = cm[klass]
        val kfgMethod = kfgKlass.getMethod(method, desc)
        val printer = JUnitTestCasePrinter(context, testClassName.substringBeforeLast('/'), testClassName.substringAfterLast('/'))
        printer.print(testMethodName, kfgMethod, Parameters(instance, args, setOf()))
        return printer.emitString()
    }

    fun convert(desc: JavaDescriptor): CallStack {
        val map = convert(setOf(desc))
        return map[desc]!!
    }

    fun convert(descs: Set<JavaDescriptor>): Map<JavaDescriptor, CallStack> {
        val reanimatorDescs = mutableMapOf<JavaDescriptor, Descriptor>()
        for (desc in descs) {
            desc.convert(reanimatorDescs)
        }
        return descs.map { it to CallStackGenerator(generatorContext).generateDescriptor(reanimatorDescs[it]!!) }.toMap()
    }

    fun JavaDescriptor.convert(map: MutableMap<JavaDescriptor, Descriptor>): Descriptor = when (val jd = this) {
        in map -> map[jd]!!
        is JavaNull -> {
            val nullDesc = descriptor { `null` }
            map[jd] = nullDesc
            nullDesc
        }
        is JavaConstant -> {
            val const = when (jd.getType()) {
                "bool" -> descriptor { const(jd.name.toBoolean()) }
                "byte" -> descriptor { const(jd.name.replace(Regex("[^\\d]"), "").toByte()) }
                "char" -> descriptor { const(jd.name.first()) }
                "short" -> descriptor { const(jd.name.replace(Regex("[^\\d]"), "").toShort()) }
                "int" -> descriptor { const(jd.name.replace(Regex("[^\\d]"), "").toInt()) }
                "long" -> descriptor { const(jd.name.replace(Regex("[^\\d]"), "").toLong()) }
                "float" -> descriptor { const(jd.name.toFloat()) }
                "double" -> descriptor { const(jd.name.toDouble()) }
                else -> TODO()
            }
            map[jd] = const
            const
        }
        is JavaObject -> descriptor {
            val klass = KexClass(jd.type.replace(".", "/"))
            val objDesc = `object`(klass)
            map[jd] = objDesc
            for ((field, value) in jd.fields) {
                val descValue = value.convert(map)
                objDesc[field.name, field.type.asType] = descValue
            }
            objDesc
        }
        is JavaArray -> descriptor {
            val arrayType = jd.type.asType as KexArray
            val arrayDesc = array(jd.length, arrayType)
            map[jd] = arrayDesc
            for ((index, value) in jd.elements) {
                arrayDesc[index] = value.convert(map)
            }
            arrayDesc
        }
        else -> TODO()
    }

    val String.asType: KexType get() = when (this) {
        "bool" -> KexBool()
        "byte" -> KexByte()
        "char" -> KexChar()
        "short" -> KexShort()
        "int" -> KexInt()
        "long" -> KexLong()
        "float" -> KexFloat()
        "double" -> KexDouble()
        else -> when {
            this.endsWith("[]") -> KexArray(this.dropLast(2).asType)
            else -> KexClass(this.replace(".", "/"))
        }
    }
}