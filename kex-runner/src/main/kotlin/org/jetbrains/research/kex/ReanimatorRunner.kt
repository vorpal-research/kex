package org.jetbrains.research.kex

import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.asm.transform.LoopDeroller
import org.jetbrains.research.kex.descriptor.*
import org.jetbrains.research.kex.ktype.*
import org.jetbrains.research.kex.random.easyrandom.EasyRandomDriver
import org.jetbrains.research.kex.reanimator.callstack.*
import org.jetbrains.research.kex.reanimator.codegen.javagen.CallStack2JavaPrinter
import org.jetbrains.research.kex.reanimator.collector.ExternalConstructorCollector
import org.jetbrains.research.kex.reanimator.collector.MethodFieldAccessCollector
import org.jetbrains.research.kex.reanimator.collector.SetterCollector
import org.jetbrains.research.kex.reanimator.descriptor.Descriptor
import org.jetbrains.research.kex.reanimator.descriptor.descriptor
import org.jetbrains.research.kex.util.getRuntime
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.Jar
import org.jetbrains.research.kfg.KfgConfig
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.analysis.LoopSimplifier
import org.jetbrains.research.kfg.util.Flags
import org.jetbrains.research.kfg.visitor.executePipeline
import java.nio.file.Path

class ReanimatorRunner(val config: Path, val target: Path) {
    val cm: ClassManager
    val context: ExecutionContext
    val generatorContext: GeneratorContext

    init {
        val jar = Jar(target, Package.defaultPackage)
        cm = ClassManager(KfgConfig(flags = Flags.readAll, failOnError = false))
        val analysisJars = listOfNotNull(jar, getRuntime())
        cm.initialize(*analysisJars.toTypedArray())

        context = ExecutionContext(cm, jar.classLoader, EasyRandomDriver())
        val psa = PredicateStateAnalysis(context.cm)

        executePipeline(cm, Package.defaultPackage) {
            +LoopSimplifier(cm)
            +LoopDeroller(cm)
            +psa
            +MethodFieldAccessCollector(context, psa)
            +SetterCollector(context)
            +ExternalConstructorCollector(cm)
        }

        generatorContext = GeneratorContext(context, psa)
    }

    fun printCallStack(stack: CallStack): String = CallStack2JavaPrinter(context).print(stack)

    fun getMethodInvocation(thisStack: CallStack, args: List<CallStack>, klass: String, methodName: String, desc: String): CallStack {
        val kfgKlass = cm[klass]
        val method = kfgKlass.getMethod(methodName, desc)
        return when {
            method.isStatic -> StaticMethodCall(method, args).wrap("static")
            else -> {
                val instance = thisStack.clone()
                instance.stack += MethodCall(method, args)
                instance
            }
        }
    }

    fun convert(desc: Desc): CallStack {
        val map = convert(setOf(desc))
        return map[desc]!!
    }

    fun convert(descs: Set<Desc>): Map<Desc, CallStack> {
        val reanimatorDescs = mutableMapOf<Desc, Descriptor>()
        for (desc in descs) {
            desc.convert(reanimatorDescs)
        }
        return descs.map { it to CallStackGenerator(generatorContext).generateDescriptor(reanimatorDescs[it]!!) }.toMap()
    }

    fun Desc.convert(map: MutableMap<Desc, Descriptor>): Descriptor = when (val jd = this) {
        in map -> map[jd]!!
        is NullDesc -> {
            val nullDesc = descriptor { `null` }
            map[jd] = nullDesc
            nullDesc
        }
        is ConstantDesc -> {
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
        is ObjectDesc -> descriptor {
            val klass = KexClass(jd.type.replace(".", "/"))
            val objDesc = `object`(klass, jd.name)
            map[jd] = objDesc
            for ((field, value) in jd.fields) {
                val descValue = value.convert(map)
                objDesc[field.name, field.type.asType] = descValue
            }
            objDesc
        }
        is ArrayDesc -> descriptor {
            val arrayType = jd.type.asType as KexArray
            val arrayDesc = array(jd.length, arrayType, jd.name)
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