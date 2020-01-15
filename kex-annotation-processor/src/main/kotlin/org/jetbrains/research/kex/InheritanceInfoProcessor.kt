package org.jetbrains.research.kex

import org.jetbrains.research.kex.util.unreachable
import java.io.File
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedAnnotationTypes
import javax.annotation.processing.SupportedOptions
import javax.annotation.processing.SupportedSourceVersion
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement

@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedAnnotationTypes("org.jetbrains.research.kex.InheritorOf")
@SupportedOptions(InheritanceInfoProcessor.KEX_RESOURCES)
class InheritanceInfoProcessor : KexProcessor() {
    companion object {
        const val KEX_RESOURCES = "kex.resources"
    }

    private val infos = mutableMapOf<String, InheritanceInfo>()

    private val targetDirectory: String
        get() = processingEnv.options[KEX_RESOURCES] ?: unreachable { error("No source directory") }

    override fun process(annotations: MutableSet<out TypeElement>?, roundEnv: RoundEnvironment?): Boolean {
        roundEnv?.run {
            getElementsAnnotatedWith(BaseType::class.java)?.forEach {
                processBaseTypeInfo(it)
            }
            getElementsAnnotatedWith(InheritorOf::class.java)?.forEach {
                processRuntimeTypeInfo(it)
            }
        }
        for ((name, info) in infos) {
            val targetFile = File("$targetDirectory$name.json")
            targetFile.bufferedWriter().use {
                it.write(info.toJson())
                it.flush()
            }
        }
        return true
    }

    private fun processBaseTypeInfo(element: Element) {
        val annotation = element.getAnnotation(BaseType::class.java)
                ?: unreachable { error("Element $element have no annotation BaseType") }

        val type = annotation.getProperty("type") as String
        infos.getOrPut(type) { InheritanceInfo(element.fullName, mutableSetOf()) }
    }

    private fun processRuntimeTypeInfo(element: Element) {
        val annotation = element.getAnnotation(InheritorOf::class.java)
                ?: unreachable { error("Element $element have no annotation InheritorOf") }

        val type = annotation.getProperty("type") as String
        getInheritanceInfo(type)?.inheritors?.add(
                Inheritor(element.simpleName.removeSuffix(type).toString(), element.fullName)
        ) ?: unreachable { error("Trying to add inheritance info with unknown base $type") }
    }

    private fun getInheritanceInfo(base: String): InheritanceInfo? {
        if (base in infos) return infos.getValue(base)
        val targetFile = File("$targetDirectory$base.json")
        return when {
            targetFile.exists() -> InheritanceInfo.fromJson(targetFile.readText())?.also {
                infos[base] = it
            }
            else -> null
        }
    }
}