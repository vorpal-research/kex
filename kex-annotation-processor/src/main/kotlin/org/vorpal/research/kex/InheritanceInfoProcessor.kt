package org.vorpal.research.kex

import org.vorpal.research.kthelper.assert.unreachable
import java.io.File
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedAnnotationTypes
import javax.annotation.processing.SupportedOptions
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement

@SupportedAnnotationTypes("org.vorpal.research.kex.BaseType", "org.vorpal.research.kex.InheritorOf")
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

            val newTypeInfo = InheritanceInfo(base, inheritors.mapTo(mutableSetOf()) { Inheritor(it.key, it.value) })
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
        types.getOrPut(bases[type]!!, ::mutableMapOf)[element.simpleName.removeSuffix(type).toString()] =
            element.fullName
    }
}
