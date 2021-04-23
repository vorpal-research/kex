package org.jetbrains.research.kex

import org.jetbrains.research.kthelper.assert.unreachable
import java.io.File
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedAnnotationTypes
import javax.annotation.processing.SupportedOptions
import javax.annotation.processing.SupportedSourceVersion
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement

@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedAnnotationTypes("org.jetbrains.research.kex.BaseType", "org.jetbrains.research.kex.InheritorOf")
@SupportedOptions(InheritanceInfoProcessor.KEX_RESOURCES)
class InheritanceInfoProcessor : KexProcessor() {
    companion object {
        const val KEX_RESOURCES = "kex.resources"
    }

    private val types = mutableMapOf<String, MutableMap<String, String>>()
    private val bases = mutableMapOf<String, String>()

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
        for ((base, inheritors) in types) {
            val targetFile = File("$targetDirectory${base.split(".").last()}.json")
            val typeInfo = when {
                targetFile.exists() -> InheritanceInfo.fromJson(targetFile.readText())
                else -> InheritanceInfo(base, setOf())
            }

            val newTypeInfo = InheritanceInfo(base, inheritors.map { Inheritor(it.key, it.value) }.toSet())
            if (typeInfo != newTypeInfo) {
                info("Updated type information for $base")
                val writer = targetFile.writer()
                writer.write(newTypeInfo.toJson())
                writer.flush()
            }
        }
        return true
    }

    private fun processBaseTypeInfo(element: Element) {
        val annotation = element.getAnnotation(BaseType::class.java)
                ?: unreachable { error("Element $element have no annotation BaseType") }

        val type = annotation.getProperty("type") as String
        bases[type] = element.fullName
    }

    private fun processRuntimeTypeInfo(element: Element) {
        val annotation = element.getAnnotation(InheritorOf::class.java)
                ?: unreachable { error("Element $element have no annotation InheritorOf") }

        val type = annotation.getProperty("type") as String
        types.getOrPut(bases[type]!!, ::mutableMapOf)[element.simpleName.removeSuffix(type).toString()] = element.fullName
    }
}