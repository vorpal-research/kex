package org.jetbrains.research.kex.reanimator.collector

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kex.util.eq
import org.jetbrains.research.kex.util.loadClass
import org.jetbrains.research.kex.util.loadKClass
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.Field
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.visitor.ClassVisitor
import org.jetbrains.research.kthelper.`try`
import org.jetbrains.research.kthelper.assert.asserted
import org.jetbrains.research.kthelper.logging.log
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KType
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaType
import org.jetbrains.research.kfg.ir.Class as KfgClass

val Field.hasSetter get() = this in SetterCollector.setters
val Field.setter get() = asserted(this.hasSetter) { SetterCollector.setters.getValue(this) }

class SetterCollector(val ctx: ExecutionContext) : ClassVisitor {
    companion object {
        val setters: Map<Field, Method> get() = settersInner

        private val settersInner = hashMapOf<Field, Method>()
    }

    override val cm: ClassManager
        get() = ctx.cm

    private val KType.kfgType
        get() = when (val jtype = this.javaType) {
            is Class<*> -> ctx.types.get(jtype)
            else -> TODO()
        }

    override fun cleanup() {}

    override fun visit(`class`: KfgClass) {
        `try` {
            val klass = ctx.loader.loadKClass(`class`)
            for (property in klass.memberProperties.filterIsInstance<KMutableProperty<*>>()) {
                for (method in `class`.methods) {
                    if (property.setter eq method) {
                        log.info("Method $method is kotlin setter for $property")
                        val field = `class`.getField(property.name, property.returnType.kfgType)
                        settersInner[field] = method
                    }
                }
            }
        }

        log.debug("$`class` is not from kotlin")
        super.visit(`class`)
    }

    override fun visitMethod(method: Method) {
        val fieldInstances = `try` {
            val klass = ctx.loader.loadClass(method.`class`)
            klass.declaredFields.filter { method.name == "set${it.name.capitalize()}" }
        }.getOrNull() ?: return
        if (fieldInstances.isEmpty()) return
        require(fieldInstances.size == 1)
        val fieldReflection = fieldInstances.first()
        val methodFA = method.fieldAccesses
        if (methodFA.size == 1
                && `try` { fieldReflection.eq(ctx.loader, methodFA.first()) }.getOrElse { false }
                && method.argTypes.size == 1
                && fieldReflection.type.isAssignableFrom(ctx.loader.loadClass(method.argTypes.first()))) {
            log.info("Method $method is java setter for $fieldReflection")
            settersInner[methodFA.first()] = method
        }
    }
}