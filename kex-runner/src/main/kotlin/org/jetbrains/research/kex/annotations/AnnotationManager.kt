package org.jetbrains.research.kex.annotations

import org.jetbrains.research.kthelper.`try`
import org.jetbrains.research.kthelper.assert.ktassert
import org.jetbrains.research.kthelper.logging.log
import org.jetbrains.research.kthelper.recast
import org.apache.commons.lang.StringEscapeUtils.unescapeJava
import org.jetbrains.research.kex.config.kexConfig
import org.reflections.Reflections
import java.io.File
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmErasure

private val printAnnotationInfo by lazy { kexConfig.getBooleanValue("annotations", "printAnnotationInfo", false) }

object AnnotationManager {
    private val constructors = hashMapOf<String, KFunction<AnnotationInfo>>()

    init {
        scanPackage(AnnotationManager::class.java.`package`.name)
    }

    val defaultLoader: AnnotationsLoader = ExternalAnnotationsLoader().apply {
        `try` {
            for (path in kexConfig.getMultipleStringValue("annotations", "path", ";")) {
                loadFrom(File(path))
            }
            if (printAnnotationInfo) {
                log.debug("Loaded annotated calls $this")
            }
        }.getOrThrow {
            log.error("Annotations not loaded")
        }
    }

    private fun scanPackage(name: String) {
        val reflections = Reflections(name)
        for (type in reflections.getSubTypesOf(AnnotationInfo::class.java)) {
            val annotation = type.getAnnotation(AnnotationFunctionality::class.java) ?: continue
            val functionality = annotation.name
            val oldValue = constructors.put(functionality, type.kotlin.primaryConstructor ?: continue)
            ktassert(oldValue == null) { log.error("Annotation functionality already described for \"$functionality\"") }
        }
    }

    fun build(name: String, parameters: Map<String, String>) : AnnotationInfo? {
        val call = constructors[name] ?: return null
        val args = hashMapOf<KParameter, Any?>()
        val params = call.parameters
        for (param in params) {
            val paramName = param.name
                    ?: throw IllegalStateException("Annotation functionality class has parameters without names")
            val value = parameters[paramName] ?: continue
            args[param] = cast(value, param.type.jvmErasure)
        }

        return call.callBy(args)
    }

    private fun getSpecialConstant(value: String): Any? {
        if (value.length > 2) {
            val char = value.first()
            if (char in 'a'..'z' || char in 'A'..'Z') {
                val lastDot = value.lastIndexOf('.')
                val className = value.substring(0 until lastDot)
                val fieldName = value.substring(lastDot + 1)
                val `class` = Class.forName(className)
                val field = `class`.getDeclaredField(fieldName)
                return field[null] as Number?
            }
            if (char == '0') {
                val str = value.replace("_", "")
                return when (value[1]) {
                    'x', 'X' -> str.substring(2).toLong(16)
                    'b', 'B' -> str.substring(2).toLong(2)
                    in '0'..'9' -> str.toLong(8)
                    else -> throw IllegalStateException("Invalid number literal \"$value\"")
                }
            }
        }
        return null
    }

    private inline fun <reified T: Number> getSpecialConstantTyped(value: String): T? =
            when (val result = getSpecialConstant(value)) {
                null -> null
                is Number -> result.recast()
                else -> throw IllegalStateException("Constant type is not java.lang.Number")
            }

    private fun clearStr(str: String) = str.replace("_", "")

    @Suppress("RemoveExplicitTypeArguments")
    private fun cast(value: String, type: KClass<*>): Any = when (type) {
        Int::class -> getSpecialConstantTyped<Int>(value)
                ?: clearStr(value).toInt()
        Byte::class -> getSpecialConstantTyped<Byte>(value)
                ?: clearStr(value).toByte()
        Short::class -> getSpecialConstantTyped<Short>(value)
                ?: clearStr(value).toShort()
        Long::class -> getSpecialConstantTyped<Long>(value)
                ?: clearStr(value).toLong()
        Float::class -> getSpecialConstantTyped<Float>(value)
                ?: clearStr(value).toFloat()
        Double::class -> getSpecialConstantTyped<Double>(value)
                ?: clearStr(value).toDouble()
        Boolean::class -> when (value) {
            "true" -> true
            "false" -> false
            else -> throw IllegalStateException("Invalid boolean constant $value")
        }
        Char::class -> when (val c = getSpecialConstant(value)) {
            is Char -> c
            null -> {
                check(value.first() == '\'' && value.last() == '\'') { "Invalid character literal" }
                val result = unescapeJava(value.substring(1..(value.length - 2)))
                check(result.length == 1) { "Character literal contains ${result.length} characters" }
                result.first()
            }
            else -> throw IllegalStateException("Specified constant is not java.lang.Character")
        }
        String::class -> when (val special = getSpecialConstant(value)) {
            is String -> special
            null -> {
                check(value.first() == '"' && value.last() == '"') { "Invalid string literal" }
                unescapeJava(value.substring(1..(value.length - 2)))
            }
            else -> throw IllegalStateException("Specified constant is not java.lang.String")
        }
        else -> throw IllegalArgumentException("Only primitive types or String supported in annotations arguments")
    }
}
