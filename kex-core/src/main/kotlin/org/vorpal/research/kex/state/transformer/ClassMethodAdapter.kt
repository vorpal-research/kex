@file:Suppress("unused")

package org.vorpal.research.kex.state.transformer

import org.vorpal.research.kex.ktype.KexInt
import org.vorpal.research.kex.ktype.KexJavaClass
import org.vorpal.research.kex.ktype.KexString
import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.state.IncrementalPredicateState
import org.vorpal.research.kex.state.StateBuilder
import org.vorpal.research.kex.state.basic
import org.vorpal.research.kex.state.predicate.CallPredicate
import org.vorpal.research.kex.state.predicate.Predicate
import org.vorpal.research.kex.state.term.CallTerm
import org.vorpal.research.kex.state.term.ConstClassTerm
import org.vorpal.research.kex.state.term.Term
import org.vorpal.research.kex.state.wrap
import org.vorpal.research.kex.util.ANNOTATION_MODIFIER
import org.vorpal.research.kex.util.ENUM_MODIFIER
import org.vorpal.research.kex.util.SYNTHETIC_MODIFIER
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.objectClass
import org.vorpal.research.kfg.type.ArrayType
import org.vorpal.research.kfg.type.ClassType
import org.vorpal.research.kfg.type.classLoaderType
import org.vorpal.research.kfg.type.classType
import org.vorpal.research.kfg.type.objectType
import org.vorpal.research.kfg.type.stringType
import org.vorpal.research.kthelper.collection.dequeOf
import java.lang.reflect.Modifier
import org.vorpal.research.kfg.ir.Class as KfgClass

abstract class ClassMethodContext(val cm: ClassManager) {
    val stringType = cm.type.stringType
    val classType = cm.type.classType
    val objectType = cm.type.objectType

    val KfgClass.forName get() = this.getMethod("forName", classType, stringType)
    val KfgClass.forNameWLoader
        get() = this.getMethod(
            "forName",
            classType,
            stringType,
            cm.type.boolType,
            cm.type.classLoaderType
        )

    val KfgClass.getCanonicalName get() = this.getMethod("getCanonicalName", stringType)
    val KfgClass.getClasses get() = this.getMethod("getClasses", classType.asArray)
    val KfgClass.getComponentType get() = this.getMethod("getComponentType", classType)
    val KfgClass.getInterfaces get() = this.getMethod("getInterfaces", classType.asArray)
    val KfgClass.getModifiers get() = this.getMethod("getModifiers", cm.type.intType)
    val KfgClass.getName get() = this.getMethod("getName", stringType)
    val KfgClass.getSuperclass get() = this.getMethod("getSuperclass", classType)
    val KfgClass.getTypeName get() = this.getMethod("getTypeName", stringType)
    val KfgClass.isAnnotationMethod get() = this.getMethod("isAnnotation", cm.type.boolType)
    val KfgClass.isAssignableFrom get() = this.getMethod("isArray", cm.type.boolType, classType)
    val KfgClass.isEnumMethod get() = this.getMethod("isEnum", cm.type.boolType)
    val KfgClass.isInstance get() = this.getMethod("isInstance", cm.type.boolType, objectType)
    val KfgClass.isInterfaceMethod get() = this.getMethod("isInterface", cm.type.boolType)
    val KfgClass.isPrimitiveMethod get() = this.getMethod("isPrimitive", cm.type.boolType)
    val KfgClass.isSyntheticMethod get() = this.getMethod("isSynthetic", cm.type.boolType)
    val KfgClass.newInstance get() = this.getMethod("newInstance", objectType)
    val KfgClass.toString get() = this.getMethod("toString", stringType)
}

class ClassMethodAdapter(
    cm: ClassManager
) : ClassMethodContext(cm), RecollectingTransformer<ClassMethodAdapter>, IncrementalTransformer {
    override val builders = dequeOf(StateBuilder())

    override fun apply(state: IncrementalPredicateState): IncrementalPredicateState {
        return IncrementalPredicateState(
            apply(state.state),
            state.queries
        )
    }

    @Suppress("unused")
    override fun transformCallPredicate(predicate: CallPredicate): Predicate {
        val call = predicate.call as CallTerm
        val args = call.arguments

        val kfgClass = (classType as ClassType).klass
        if (call.owner.type != kfgClass.kexType) return predicate

        val `this` = call.owner
        val calledMethod = call.method

        currentBuilder += when (calledMethod) {
            kfgClass.forName -> forName(predicate.lhv, args.first())
            kfgClass.forNameWLoader -> forName(predicate.lhv, args.first())
            kfgClass.getCanonicalName -> getCanonicalName(predicate.lhv, `this`)
            kfgClass.getClasses -> if (`this` is ConstClassTerm) getClasses(
                predicate.lhv,
                `this`.constantType
            ) else predicate.wrap()

            kfgClass.getComponentType -> if (`this` is ConstClassTerm) getComponentType(
                predicate.lhv,
                `this`.constantType
            ) else predicate.wrap()

            kfgClass.getInterfaces -> if (`this` is ConstClassTerm) getInterfaces(
                predicate.lhv,
                `this`.constantType
            ) else predicate.wrap()

            kfgClass.getModifiers -> getModifiers(predicate.lhv, `this`)
            kfgClass.getName -> getName(predicate.lhv, `this`)
            kfgClass.getSuperclass -> if (`this` is ConstClassTerm) getSuperclass(
                predicate.lhv,
                `this`.constantType
            ) else predicate.wrap()

            kfgClass.getTypeName -> getName(predicate.lhv, `this`)
            kfgClass.isAnnotationMethod -> isAnnotated(predicate.lhv, `this`)
            kfgClass.isEnumMethod -> isEnum(predicate.lhv, `this`)
            kfgClass.isInterfaceMethod -> isInterface(predicate.lhv, `this`)
            kfgClass.isSyntheticMethod -> isSynthetic(predicate.lhv, `this`)
            kfgClass.newInstance -> newInstance(predicate.lhv, `this`)
            kfgClass.toString -> toString(predicate.lhv, `this`)
            else -> predicate.wrap()
        }
        return nothing()
    }

    private fun forName(lhv: Term, name: Term) = basic {
        state { lhv.new() }
        val field = generate(KexString())
        state { field equality lhv.field(KexString(), ConstClassTerm.NAME_PROPERTY).load() }
        assume { field equality name }
    }

    private fun getCanonicalName(lhv: Term, instance: Term) = basic {
        state { lhv equality instance.field(KexString(), ConstClassTerm.NAME_PROPERTY).load() }
    }

    private fun getClasses(lhv: Term, constClass: KexType) = basic {
        val members = when (val kfgType = constClass.getKfgType(cm.type)) {
            is ClassType -> kfgType.klass.allAncestors.flatMap { it.innerClasses.keys }
                .filter { it.isPublic } + kfgType.klass.innerClasses.keys.filter { it.isPublic }

            else -> listOf()
        }
        val length = generate(KexInt)
        state { length equality lhv.length() }
        assume { length equality const(members.size) }
        for ((index, member) in members.withIndex()) {
            val load = generate(KexJavaClass())
            state { load equality lhv[index].load() }
            assume { load equality `class`(member) }
        }
    }

    private fun getComponentType(lhv: Term, constClass: KexType) = basic {
        val kfgType = constClass.getKfgType(cm.type)
        state {
            lhv equality if (kfgType is ArrayType) `class`(KexJavaClass(), kfgType.component.kexType) else const(null)
        }
    }

    private fun getInterfaces(lhv: Term, constClass: KexType) = basic {
        val interfaces = when (val kfgType = constClass.getKfgType(cm.type)) {
            is ClassType -> if (kfgType.klass.isInterface) listOf(kfgType.klass)
            else kfgType.klass.interfaces

            else -> listOf()
        }
        val length = generate(KexInt)
        state { length equality lhv.length() }
        assume { length equality const(interfaces.size) }
        for ((index, member) in interfaces.withIndex()) {
            val load = generate(KexJavaClass())
            state { load equality lhv[index].load() }
            assume { load equality `class`(member) }
        }
    }

    private fun getModifiers(lhv: Term, instance: Term) = basic {
        state {
            lhv equality instance.field(KexInt, ConstClassTerm.MODIFIERS_PROPERTY).load()
        }
    }

    private fun getName(lhv: Term, instance: Term) = basic {
        state {
            lhv equality instance.field(KexString(), ConstClassTerm.NAME_PROPERTY).load()
        }
    }

    private fun getSuperclass(lhv: Term, constClass: KexType) = basic {
        val kfgType = constClass.getKfgType(cm.type)
        state {
            lhv equality when (kfgType) {
                is ArrayType -> `class`(cm.objectClass)
                is ClassType -> kfgType.klass.superClass?.let { `class`(it) } ?: const(null)
                else -> const(null)
            }
        }
    }

    fun getTypeName(lhv: Term, instance: Term) = basic {
        state {
            lhv equality instance.field(KexString(), ConstClassTerm.NAME_PROPERTY).load()
        }
    }

    private fun isAnnotated(lhv: Term, instance: Term) = basic {
        val modifiers = generate(KexInt)
        val andRes = generate(KexInt)
        state {
            modifiers equality instance.field(KexInt, ConstClassTerm.MODIFIERS_PROPERTY).load()
        }
        state {
            andRes equality (modifiers and ANNOTATION_MODIFIER)
        }
        state {
            lhv equality (modifiers eq ANNOTATION_MODIFIER)
        }
    }

    private fun isEnum(lhv: Term, instance: Term) = basic {
        val modifiers = generate(KexInt)
        val andRes = generate(KexInt)
        state {
            modifiers equality instance.field(KexInt, ConstClassTerm.MODIFIERS_PROPERTY).load()
        }
        state {
            andRes equality (modifiers and ENUM_MODIFIER)
        }
        state {
            lhv equality (modifiers eq ENUM_MODIFIER)
        }
    }

    private fun isInterface(lhv: Term, instance: Term) = basic {
        val modifiers = generate(KexInt)
        val andRes = generate(KexInt)
        state {
            modifiers equality instance.field(KexInt, ConstClassTerm.MODIFIERS_PROPERTY).load()
        }
        state {
            andRes equality (modifiers and Modifier.INTERFACE)
        }
        state {
            lhv equality (modifiers eq Modifier.INTERFACE)
        }
    }

    private fun isSynthetic(lhv: Term, instance: Term) = basic {
        val modifiers = generate(KexInt)
        val andRes = generate(KexInt)
        state {
            modifiers equality instance.field(KexInt, ConstClassTerm.MODIFIERS_PROPERTY).load()
        }
        state {
            andRes equality (modifiers and SYNTHETIC_MODIFIER)
        }
        state {
            lhv equality (modifiers eq SYNTHETIC_MODIFIER)
        }
    }

    private fun newInstance(lhv: Term, instance: Term) = basic {
        state {
            lhv.new()
        }
        val klass = generate(KexJavaClass())
        state { klass equality lhv.klass }
        assume { klass equality instance }
    }

    private fun toString(lhv: Term, instance: Term) = basic {
        state {
            lhv equality instance.field(KexString(), ConstClassTerm.NAME_PROPERTY).load()
        }
    }
}
