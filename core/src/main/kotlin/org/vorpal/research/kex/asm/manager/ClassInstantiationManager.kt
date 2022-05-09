package org.vorpal.research.kex.asm.manager

import org.vorpal.research.kex.asm.util.Visibility
import org.vorpal.research.kex.asm.util.visibility
import org.vorpal.research.kex.ktype.*
import org.vorpal.research.kex.ktype.KexRtManager.rtMapped
import org.vorpal.research.kex.util.*
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.instruction.NewInst
import org.vorpal.research.kfg.ir.value.instruction.ReturnInst
import org.vorpal.research.kfg.type.*
import org.vorpal.research.kfg.visitor.ClassVisitor
import org.vorpal.research.kthelper.`try`


val instantiationManager: ClassInstantiationManager get() = ClassInstantiationManagerImpl

class NoConcreteInstanceException(val klass: Class) : Exception()

interface ClassInstantiationManager {
    fun isDirectlyInstantiable(klass: Class, visibilityLevel: Visibility): Boolean
    fun isInstantiable(klass: Class): Boolean
    fun getExternalCtors(klass: Class): Set<Method>
    operator fun get(klass: Class): Class
    fun get(klass: Class, excludes: Set<Class>): Class
    fun get(tf: TypeFactory, type: Type, excludes: Set<Class>): Type = when (type) {
        is ClassType -> get(type.klass, excludes).type
        is ArrayType -> tf.getArrayType(get(tf, type.component, excludes))
        else -> type
    }

    fun getConcreteClass(klass: KexClass, cm: ClassManager): KexClass = get(klass.kfgClass(cm.type)).kexType
    fun getConcreteType(type: KexType, cm: ClassManager): KexType = when (type) {
        is KexClass -> getConcreteClass(type, cm)
        is KexReference -> KexReference(getConcreteType(type.reference, cm))
        else -> type
    }
}

private object ClassInstantiationManagerImpl : ClassInstantiationManager {
    private val predefinedConcreteInstanceInfo = with(SystemTypeNames) {
        mutableMapOf(
            collectionClass to setOf(arrayListClass.rtMapped),
            listClass to setOf(arrayListClass.rtMapped),
            queueClass to setOf(arrayListClass.rtMapped),
            dequeClass to setOf(arrayDequeClass.rtMapped),
            setClass to setOf(hashSetClass.rtMapped),
            sortedSetClass to setOf(treeSetClass.rtMapped),
            navigableSetClass to setOf(treeSetClass.rtMapped),
            mapClass to setOf(hashMapClass.rtMapped),
            sortedMapClass to setOf(treeSetClass.rtMapped),
            navigableMapClass to setOf(treeSetClass.rtMapped),
            unmodifiableCollection to setOf(unmodifiableList.rtMapped),
            unmodifiableList to setOf(unmodifiableList.rtMapped),
            unmodifiableSet to setOf(unmodifiableSet.rtMapped),
            unmodifiableMap to setOf(unmodifiableMap.rtMapped),
            charSequence to setOf(stringClass.rtMapped)
        )
    }
    private val classInstantiationInfo = mutableMapOf<Class, MutableSet<Class>>()
    private val externalConstructors = mutableMapOf<Class, MutableSet<Method>>()

    override fun isDirectlyInstantiable(klass: Class, visibilityLevel: Visibility): Boolean =
        klass.visibility >= visibilityLevel && !klass.isAbstract && !klass.isInterface

    override fun isInstantiable(klass: Class) = when (klass.fullName) {
        in predefinedConcreteInstanceInfo -> true
        else -> classInstantiationInfo[klass].isNullOrEmpty()
    }

    override fun getExternalCtors(klass: Class): Set<Method> = externalConstructors.getOrDefault(klass, setOf())

    override fun get(klass: Class): Class = `try` {
        when (klass.fullName) {
            in predefinedConcreteInstanceInfo -> predefinedConcreteInstanceInfo.getValue(klass.fullName).random()
                .let { klass.cm[it] }
            else -> classInstantiationInfo.getOrDefault(klass, setOf()).let {
                if (klass in it) klass
                else it.random()
            }
        }
    }.getOrElse {
        throw NoConcreteInstanceException(klass)
    }

    override fun get(klass: Class, excludes: Set<Class>): Class = `try` {
        when (klass.fullName) {
            in predefinedConcreteInstanceInfo ->
                (predefinedConcreteInstanceInfo.getValue(klass.fullName) - excludes.map { it.fullName }.toSet())
                    .random()
                    .let { klass.cm[it] }
            else ->
                (classInstantiationInfo.getOrDefault(klass, setOf()) - excludes).let {
                    if (klass in it) klass
                    else it.random()
                }
        }
    }.getOrElse {
        throw NoConcreteInstanceException(klass)
    }

    operator fun set(parent: Class, concrete: Class) {
        classInstantiationInfo.getOrPut(parent, ::mutableSetOf).add(concrete)
    }

    operator fun set(klass: Class, externalCtor: Method) {
        externalConstructors.getOrPut(klass, ::mutableSetOf).add(externalCtor)
    }
}

class ClassInstantiationDetector(override val cm: ClassManager, val visibilityLevel: Visibility) : ClassVisitor {
    override fun cleanup() {}

    override fun visit(klass: Class) {
        if (ClassInstantiationManagerImpl.isDirectlyInstantiable(klass, visibilityLevel))
            addInstantiableClass(klass, klass)
        super.visit(klass)
    }

    override fun visitMethod(method: Method) {
        val returnType = (method.returnType as? ClassType) ?: return
        if (visibilityLevel > method.visibility) return
        if (visibilityLevel > method.klass.visibility) return
        if (!method.isStatic || method.argTypes.any { it.isSubtypeOf(returnType) } || method.isSynthetic) return

        var returnClass = returnType.klass
        method.flatten().firstOrNull { it is ReturnInst }?.let {
            it as ReturnInst
            if (it.returnValue is NewInst) {
                returnClass = (it.returnValue.type as ClassType).klass
            }
        }
        addInstantiableClass(returnClass, returnClass)
        addExternalCtor(returnClass, method)
    }

    private fun addInstantiableClass(klass: Class, instantiableKlass: Class) {
        if (!instantiableKlass.isEnum) {
            for (parent in klass.allAncestors) {
                addInstantiableClass(parent, instantiableKlass)
            }
        }
        ClassInstantiationManagerImpl[klass] = instantiableKlass
    }

    private fun addExternalCtor(klass: Class, method: Method) {
        if (!klass.isEnum) {
            for (parent in klass.allAncestors) {
                addExternalCtor(parent, method)
            }
        }
        ClassInstantiationManagerImpl[klass] = method
    }
}
