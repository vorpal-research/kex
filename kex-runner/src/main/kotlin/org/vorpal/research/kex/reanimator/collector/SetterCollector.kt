package org.vorpal.research.kex.reanimator.collector

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.util.eq
import org.vorpal.research.kex.util.loadClass
import org.vorpal.research.kex.util.loadKClass
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.ir.Field
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.visitor.ClassVisitor
import org.vorpal.research.kthelper.assert.asserted
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.`try`
import java.lang.Class
import java.util.*
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KType
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaType
import org.vorpal.research.kfg.ir.Class as KfgClass

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
        get() = when (val jType = this.javaType) {
            is Class<*> -> ctx.types.get(jType)
            else -> TODO()
        }

    override fun cleanup() {}

    override fun visit(klass: KfgClass) {
        `try` {
            val kClass = ctx.loader.loadKClass(klass)
            for (property in kClass.memberProperties.filterIsInstance<KMutableProperty<*>>()) {
                for (method in klass.methods) {
                    if (property.setter eq method) {
                        log.info("Method $method is kotlin setter for $property")
                        val field = klass.getField(property.name, property.returnType.kfgType)
                        settersInner[field] = method
                    }
                }
            }
        }

        log.debug("{} is not from kotlin", klass)
        super.visit(klass)
    }

    override fun visitMethod(method: Method) {
        val fieldInstances = `try` {
            val klass = ctx.loader.loadClass(method.klass)
            klass.declaredFields.filter {
                method.name == "set${
                    it.name.replaceFirstChar { character ->
                        if (character.isLowerCase()) character.titlecase(
                            Locale.getDefault()
                        ) else character.toString()
                    }
                }"
            }
        }.getOrNull() ?: return
        if (fieldInstances.isEmpty()) return
        require(fieldInstances.size == 1)
        val fieldReflection = fieldInstances.first()
        val methodFA = method.fieldAccesses
        if (methodFA.size == 1
            && `try` { fieldReflection.eq(ctx.loader, methodFA.first()) }.getOrElse { false }
            && method.argTypes.size == 1
            && fieldReflection.type.isAssignableFrom(ctx.loader.loadClass(method.argTypes.first()))
        ) {
            log.info("Method $method is java setter for $fieldReflection")
            settersInner[methodFA.first()] = method
        }
    }
}
