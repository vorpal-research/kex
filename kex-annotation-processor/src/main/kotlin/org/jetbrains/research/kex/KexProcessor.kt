package org.jetbrains.research.kex

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

//    protected fun <T : Annotation> getAnnotationProperties(instance: T, klass: KClass<T>): Map<String, Any?> {
//        val parameters = mutableMapOf<String, Any?>()
//
//        info("$klass")
//        info("${klass.declaredMemberProperties}")
//        info("${klass.memberFunctions}")
//        for (property in klass.declaredMemberProperties) {
//            parameters[property.name] = property.get(instance)
////            info("Searching for property $property of $instance")
////            val prop = klass.memberFunctions.first { it.name == property.name }
////            parameters[property.name] = prop.call(instance)
////                    ?: unreachable { error("Annotation $instance have no property named ${property.name}") }
//        }
//
//        return parameters
//    }

    protected fun <T : Annotation> T.getProperty(name: String): Any? {
        val prop = this::class.memberFunctions.first { it.name == name }
        return prop.call(this)
    }
}