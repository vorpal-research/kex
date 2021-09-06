package org.jetbrains.research.kex

import org.jetbrains.research.kthelper.assert.unreachable
import javax.annotation.processing.AbstractProcessor
import javax.lang.model.element.Element
import javax.tools.Diagnostic
import kotlin.reflect.KClass
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.memberFunctions

abstract class KexProcessor : AbstractProcessor() {
    private val Element.`package` get() = "${processingEnv.elementUtils.getPackageOf(this)}"
    protected val Element.fullName get() = "$`package`.$simpleName"

    protected fun error(msg: String) = processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, msg)
    protected fun info(msg: String) = processingEnv.messager.printMessage(Diagnostic.Kind.NOTE, msg)

    protected fun <T : Annotation> getAnnotationProperties(instance: T, klass: KClass<T>): Map<String, Any> {
        val parameters = mutableMapOf<String, Any>()

        for (property in klass.declaredMemberProperties) {
            parameters[property.name] = instance.getProperty(property.name) ?:
                    unreachable { error("Could not get property $property of annotation $instance") }
        }

        return parameters
    }

    protected fun <T : Annotation> T.getProperty(name: String): Any? {
        val prop = this::class.memberFunctions.first { it.name == name }
        return prop.call(this)
    }
}