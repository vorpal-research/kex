package org.vorpal.research.kex.asm.manager

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.util.AccessModifier
import org.vorpal.research.kex.asm.util.accessModifier
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
import kotlin.random.Random


val instantiationManager: ClassInstantiationManager get() = StringClassInstantiationManagerImpl

class NoConcreteInstanceException(val klass: Class) : Exception()

interface ClassInstantiationManager {
    fun isDirectlyInstantiable(klass: Class, accessLevel: AccessModifier): Boolean
    fun isInstantiable(klass: Class): Boolean
    fun getExternalCtors(klass: Class): Set<Method>
    operator fun get(klass: Class, random: Random): Class
    fun get(klass: Class, excludes: Set<Class>, random: Random): Class
    fun get(tf: TypeFactory, type: Type, excludes: Set<Class>, random: Random): Type = when (type) {
        is ClassType -> get(type.klass, excludes, random).type
        is ArrayType -> tf.getArrayType(get(tf, type.component, excludes, random))
        else -> type
    }

    fun getConcreteClass(klass: KexClass, cm: ClassManager, random: Random): KexClass =
        get(klass.kfgClass(cm.type), random).kexType

    fun getConcreteType(type: KexType, cm: ClassManager, random: Random): KexType = when (type) {
        is KexClass -> getConcreteClass(type, cm, random)
        is KexReference -> KexReference(getConcreteType(type.reference, cm, random))
        else -> type
    }
}

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

@Deprecated(
    message = "use StringClassInstantiationManagerImpl that is more efficient with lazy kfg",
    replaceWith = ReplaceWith("org.vorpal.research.kex.asm.manager.StringClassInstantiationManagerImpl")
)
private object ClassInstantiationManagerImpl : ClassInstantiationManager {
    private val classInstantiationInfo = mutableMapOf<Class, MutableSet<Class>>()
    private val externalConstructors = mutableMapOf<Class, MutableSet<Method>>()

    override fun isDirectlyInstantiable(klass: Class, accessLevel: AccessModifier): Boolean =
        accessLevel.canAccess(klass.accessModifier) && !klass.isAbstract && !klass.isInterface

    override fun isInstantiable(klass: Class) = when (klass.fullName) {
        in predefinedConcreteInstanceInfo -> true
        else -> classInstantiationInfo[klass].isNullOrEmpty()
    }

    override fun getExternalCtors(klass: Class): Set<Method> = externalConstructors.getOrDefault(klass, setOf())

    override fun get(klass: Class, random: Random): Class = `try` {
        when (klass.fullName) {
            in predefinedConcreteInstanceInfo -> predefinedConcreteInstanceInfo.getValue(klass.fullName).random(random)
                .let { klass.cm[it] }
            else -> classInstantiationInfo.getOrDefault(klass, setOf()).let {
                if (klass in it) klass
                else it.random(random)
            }
        }
    }.getOrElse {
        throw NoConcreteInstanceException(klass)
    }

    override fun get(klass: Class, excludes: Set<Class>, random: Random): Class = `try` {
        when (klass.fullName) {
            in predefinedConcreteInstanceInfo ->
                (predefinedConcreteInstanceInfo.getValue(klass.fullName) - excludes.map { it.fullName }.toSet())
                    .random(random)
                    .let { klass.cm[it] }
            else ->
                (classInstantiationInfo.getOrDefault(klass, setOf()) - excludes).let {
                    if (klass in it) klass
                    else it.random(random)
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

private object StringClassInstantiationManagerImpl : ClassInstantiationManager {
    private val classInstantiationInfo = mutableMapOf<String, MutableSet<String>>()
    private val externalConstructors = mutableMapOf<String, MutableSet<Triple<String, String, String>>>()

    override fun isDirectlyInstantiable(klass: Class, accessLevel: AccessModifier): Boolean =
        accessLevel.canAccess(klass.accessModifier) && !klass.isAbstract && !klass.isInterface

    override fun isInstantiable(klass: Class) = when (val fullName = klass.fullName) {
        in predefinedConcreteInstanceInfo -> true
        else -> classInstantiationInfo[fullName].isNullOrEmpty()
    }

    override fun getExternalCtors(klass: Class): Set<Method> =
        externalConstructors.getOrDefault(klass.fullName, setOf()).map { (klassName, methodName, desc) ->
            klass.cm[klassName].getMethod(methodName, desc)
        }.toSet()

    override fun get(klass: Class, random: Random): Class = `try` {
        val concreteClassName = when (val fullName = klass.fullName) {
            in predefinedConcreteInstanceInfo -> predefinedConcreteInstanceInfo.getValue(fullName)
                .random(random)
            else -> classInstantiationInfo.getOrDefault(fullName, setOf()).let {
                if (fullName in it) fullName
                else it.random(random)
            }
        }
        klass.cm[concreteClassName]
    }.getOrElse {
        throw NoConcreteInstanceException(klass)
    }

    override fun get(klass: Class, excludes: Set<Class>, random: Random): Class = `try` {
        val excludeNames = excludes.map { it.fullName }.toSet()
        val concreteClassName = when (val fullName = klass.fullName) {
            in predefinedConcreteInstanceInfo -> (predefinedConcreteInstanceInfo.getValue(fullName) - excludeNames)
                .random(random)
            else -> (classInstantiationInfo.getOrDefault(fullName, setOf()) - excludeNames).let {
                if (fullName in it) fullName
                else it.random(random)
            }
        }
        klass.cm[concreteClassName]
    }.getOrElse {
        throw NoConcreteInstanceException(klass)
    }

    operator fun set(parent: Class, concrete: Class) {
        classInstantiationInfo.getOrPut(parent.fullName, ::mutableSetOf).add(concrete.fullName)
    }

    operator fun set(klass: Class, externalCtor: Method) {
        externalConstructors.getOrPut(klass.fullName, ::mutableSetOf).add(
            Triple(externalCtor.klass.fullName, externalCtor.name, externalCtor.asmDesc)
        )
    }
}

class ClassInstantiationDetector(
    private val ctx: ExecutionContext
) : ClassVisitor {
    override val cm: ClassManager get() = ctx.cm
    val accessLevel: AccessModifier get() = ctx.accessLevel

    override fun cleanup() {}

    override fun visit(klass: Class) {
        if (StringClassInstantiationManagerImpl.isDirectlyInstantiable(klass, accessLevel))
            addInstantiableClass(klass, klass)
        super.visit(klass)
    }

    override fun visitMethod(method: Method) {
        val returnType = (method.returnType as? ClassType) ?: return
        if (!accessLevel.canAccess(method.accessModifier)) return
        if (!accessLevel.canAccess(method.klass.accessModifier)) return
        if (!method.isStatic || method.argTypes.any { it.isSubtypeOf(returnType) } || method.isSynthetic) return

        var returnClass = returnType.klass
        method.body.flatten().firstOrNull { it is ReturnInst }?.let {
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
        StringClassInstantiationManagerImpl[klass] = instantiableKlass
    }

    private fun addExternalCtor(klass: Class, method: Method) {
        if (!klass.isEnum) {
            for (parent in klass.allAncestors) {
                addExternalCtor(parent, method)
            }
        }
        StringClassInstantiationManagerImpl[klass] = method
    }
}
