package org.jetbrains.research.kex.asm.manager

import org.jetbrains.research.kex.asm.util.Visibility
import org.jetbrains.research.kex.asm.util.visibility
import org.jetbrains.research.kex.ktype.KexClass
import org.jetbrains.research.kex.ktype.KexReference
import org.jetbrains.research.kex.ktype.KexRtManager.rtMapped
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.ktype.kexType
import org.jetbrains.research.kex.util.unmodifiableCollection
import org.jetbrains.research.kex.util.unmodifiableList
import org.jetbrains.research.kex.util.unmodifiableMap
import org.jetbrains.research.kex.util.unmodifiableSet
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.instruction.NewInst
import org.jetbrains.research.kfg.ir.value.instruction.ReturnInst
import org.jetbrains.research.kfg.type.ClassType
import org.jetbrains.research.kfg.type.SystemTypeNames
import org.jetbrains.research.kfg.visitor.ClassVisitor
import org.jetbrains.research.kthelper.`try`


val instantiationManager: ClassInstantiationManager get() = ClassInstantiationManagerImpl

class NoConcreteInstanceException(val klass: Class) : Exception()

interface ClassInstantiationManager {
    fun isInstantiable(klass: Class): Boolean
    fun getExternalCtors(klass: Class): Set<Method>
    operator fun get(klass: Class): Class

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
        )
    }
    private val classInstantiationInfo = mutableMapOf<Class, MutableSet<Class>>()
    private val externalConstructors = mutableMapOf<Class, MutableSet<Method>>()

    override fun isInstantiable(klass: Class) = when (klass.fullName) {
        in predefinedConcreteInstanceInfo -> true
        else -> classInstantiationInfo[klass].isNullOrEmpty()
    }

    override fun getExternalCtors(klass: Class): Set<Method> = externalConstructors.getOrDefault(klass, setOf())

    override fun get(klass: Class): Class = `try` {
        when (klass.fullName) {
            in predefinedConcreteInstanceInfo -> predefinedConcreteInstanceInfo.getValue(klass.fullName).random()
                .let { klass.cm[it] }
            else -> classInstantiationInfo.getOrDefault(klass, setOf()).random()
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
        if (klass.visibility >= visibilityLevel && !klass.isAbstract && !klass.isInterface)
            addInstantiableClass(klass)
        super.visit(klass)
    }

    override fun visitMethod(method: Method) {
        val returnType = (method.returnType as? ClassType) ?: return
        if (visibilityLevel > method.visibility) return
        if (!method.isStatic || method.argTypes.any { it.isSubtypeOf(returnType) } || method.isSynthetic) return

        var returnClass = returnType.klass
        method.flatten().firstOrNull { it is ReturnInst }?.let {
            it as ReturnInst
            if (it.returnValue is NewInst) {
                returnClass = (it.returnValue.type as ClassType).klass
            }
        }
        addInstantiableClass(returnClass)
        addExternalCtor(returnClass, method)
    }

    private fun addInstantiableClass(klass: Class) {
        for (parent in klass.allAncestors) {
            ClassInstantiationManagerImpl[parent] = klass
        }
        ClassInstantiationManagerImpl[klass] = klass
    }

    private fun addExternalCtor(klass: Class, method: Method) {
        for (parent in klass.allAncestors) {
            ClassInstantiationManagerImpl[klass] = method
        }
        ClassInstantiationManagerImpl[klass] = method
    }
}
