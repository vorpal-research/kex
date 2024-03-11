package org.vorpal.research.kex.asm.manager

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.util.AccessModifier
import org.vorpal.research.kex.asm.util.accessModifier
import org.vorpal.research.kex.ktype.KexClass
import org.vorpal.research.kex.ktype.KexReference
import org.vorpal.research.kex.ktype.KexRtManager.rtMapped
import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.util.isSubtypeOfCached
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kfg.ir.ConcreteClass
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.type.ArrayType
import org.vorpal.research.kfg.type.ClassType
import org.vorpal.research.kfg.type.SystemTypeNames
import org.vorpal.research.kfg.type.Type
import org.vorpal.research.kfg.visitor.ClassVisitor
import org.vorpal.research.kthelper.`try`
import kotlin.random.Random


val instantiationManager: ClassInstantiationManager get() = StringClassInstantiationManagerImpl

class NoConcreteInstanceException(val klass: Class) : Exception()

interface ClassInstantiationManager {
    fun isDirectlyInstantiable(klass: Class, accessLevel: AccessModifier): Boolean
    fun isInstantiable(klass: Class): Boolean
    fun getExternalCtors(klass: Class): Set<Method>
    operator fun get(klass: Class, accessLevel: AccessModifier, random: Random): Class
    fun get(klass: Class, accessLevel: AccessModifier, excludes: Set<Class>, random: Random): Class
    fun get(type: Type, accessLevel: AccessModifier, excludes: Set<Class>, random: Random): Type =
        when (type) {
            is ClassType -> get(type.klass, accessLevel, excludes, random).asType
            is ArrayType -> get(type.component, accessLevel, excludes, random).asArray
            else -> type
        }

    fun getAll(type: Type, accessLevel: AccessModifier, excludes: Set<Class>): Set<Type> =
        when (type) {
            is ClassType -> getAllConcreteSubtypes(type.klass, accessLevel)
                .filter { it !in excludes }
                .mapTo(mutableSetOf()) { it.asType }

            is ArrayType -> getAll(type.component, accessLevel, excludes)
                .mapTo(mutableSetOf()) { it.asArray }

            else -> setOf(type)
        }

    fun getAllConcreteSubtypes(klass: Class, accessLevel: AccessModifier): Set<Class>

    fun getConcreteClass(klass: KexClass, cm: ClassManager, accessLevel: AccessModifier, random: Random): KexClass =
        get(klass.kfgClass(cm.type), accessLevel, random).kexType

    fun getConcreteType(type: KexType, cm: ClassManager, accessLevel: AccessModifier, random: Random): KexType =
        when (type) {
            is KexClass -> getConcreteClass(type, cm, accessLevel, random)
            is KexReference -> KexReference(getConcreteType(type.reference, cm, accessLevel, random))
            else -> type
        }
}

private val predefinedConcreteInstanceInfo = with(SystemTypeNames) {
    mutableMapOf(
        collectionClass to setOf(arrayListClass.rtMapped),
        abstractCollectionClass to setOf(arrayListClass.rtMapped),
        listClass to setOf(arrayListClass.rtMapped),
        abstractListClass to setOf(arrayListClass.rtMapped),
        queueClass to setOf(linkedListClass.rtMapped),
        abstractQueueClass to setOf(linkedListClass.rtMapped),
        arrayListClass to setOf(arrayListClass.rtMapped),
        linkedListClass to setOf(linkedListClass.rtMapped),
        arrayListClass.rtMapped to setOf(arrayListClass.rtMapped),
        linkedListClass.rtMapped to setOf(linkedListClass.rtMapped),
        dequeClass to setOf(arrayDequeClass.rtMapped),
        arrayDequeClass to setOf(arrayDequeClass.rtMapped),
        arrayDequeClass.rtMapped to setOf(arrayDequeClass.rtMapped),
        setClass to setOf(hashSetClass.rtMapped),
        abstractSetClass to setOf(hashSetClass.rtMapped),
        sortedSetClass to setOf(treeSetClass.rtMapped),
        hashSetClass to setOf(hashSetClass.rtMapped),
        treeSetClass to setOf(treeSetClass.rtMapped),
        navigableSetClass to setOf(treeSetClass.rtMapped),
        hashSetClass.rtMapped to setOf(hashSetClass.rtMapped),
        treeSetClass.rtMapped to setOf(treeSetClass.rtMapped),
        mapClass to setOf(hashMapClass.rtMapped),
        abstractMapClass to setOf(hashMapClass.rtMapped),
        sortedMapClass to setOf(treeMapClass.rtMapped),
        navigableMapClass to setOf(treeMapClass.rtMapped),
        hashMapClass to setOf(hashMapClass.rtMapped),
        treeMapClass to setOf(treeMapClass.rtMapped),
        hashMapClass.rtMapped to setOf(hashMapClass.rtMapped),
        treeMapClass.rtMapped to setOf(treeMapClass.rtMapped),
        unmodifiableCollection to setOf(unmodifiableList.rtMapped),
        unmodifiableList to setOf(unmodifiableList.rtMapped),
        unmodifiableSet to setOf(unmodifiableSet.rtMapped),
        unmodifiableMap to setOf(unmodifiableMap.rtMapped),
        charSequence to setOf(stringClass.rtMapped),
        stringClass to setOf(stringClass.rtMapped),

        abstractStringBuilderClass to setOf(stringBuilder.rtMapped),
        stringBuilder to setOf(stringBuilder.rtMapped),
        stringBuffer to setOf(stringBuffer.rtMapped),

        booleanClass to setOf(booleanClass.rtMapped),
        byteClass to setOf(byteClass.rtMapped),
        charClass to setOf(charClass.rtMapped),
        doubleClass to setOf(doubleClass.rtMapped),
        floatClass to setOf(floatClass.rtMapped),
        integerClass to setOf(integerClass.rtMapped),
        longClass to setOf(longClass.rtMapped),
        shortClass to setOf(shortClass.rtMapped),
        numberClass to setOf(
            byteClass.rtMapped,
            doubleClass.rtMapped,
            floatClass.rtMapped,
            integerClass.rtMapped,
            longClass.rtMapped,
            shortClass.rtMapped
        ),

        atomicBooleanClass to setOf(atomicBooleanClass.rtMapped),
        atomicIntegerClass to setOf(atomicIntegerClass),
        atomicIntegerArrayClass to setOf(atomicIntegerArrayClass),
        atomicLongClass to setOf(atomicLongClass),
        atomicLongArrayClass to setOf(atomicLongArrayClass),
        atomicReferenceClass to setOf(atomicReferenceClass),
        atomicReferenceArrayClass to setOf(atomicReferenceArrayClass),
        atomicStampedReferenceClass to setOf(atomicStampedReferenceClass)
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

    override fun get(klass: Class, accessLevel: AccessModifier, random: Random): Class = `try` {
        when (klass.fullName) {
            in predefinedConcreteInstanceInfo -> predefinedConcreteInstanceInfo.getValue(klass.fullName)
                .filter { isDirectlyInstantiable(klass.cm[it], accessLevel) }
                .random(random)
                .let { klass.cm[it] }

            else -> classInstantiationInfo.getOrDefault(klass, setOf())
                .filter { isDirectlyInstantiable(it, accessLevel) }
                .let {
                    if (klass in it) klass
                    else it.random(random)
                }
        }
    }.getOrElse {
        throw NoConcreteInstanceException(klass)
    }

    override fun get(klass: Class, accessLevel: AccessModifier, excludes: Set<Class>, random: Random): Class = `try` {
        when (klass.fullName) {
            in predefinedConcreteInstanceInfo ->
                (predefinedConcreteInstanceInfo.getValue(klass.fullName) - excludes.mapTo(mutableSetOf()) { it.fullName })
                    .filter { isDirectlyInstantiable(klass.cm[it], accessLevel) }
                    .random(random)
                    .let { klass.cm[it] }

            else -> (classInstantiationInfo.getOrDefault(klass, setOf()) - excludes)
                .filter { isDirectlyInstantiable(it, accessLevel) }
                .filterIsInstance<ConcreteClass>()
                .let {
                    if (klass in it) klass
                    else it.random(random)
                }
        }
    }.getOrElse {
        throw NoConcreteInstanceException(klass)
    }

    override fun getAllConcreteSubtypes(klass: Class, accessLevel: AccessModifier): Set<Class> =
        when (klass.fullName) {
            in predefinedConcreteInstanceInfo -> predefinedConcreteInstanceInfo.getValue(klass.fullName)
                .filter { isDirectlyInstantiable(klass.cm[it], accessLevel) }
                .mapTo(mutableSetOf()) { klass.cm[it] }

            else -> classInstantiationInfo.getOrDefault(klass, setOf())
                .filterTo(mutableSetOf()) { isDirectlyInstantiable(it, accessLevel) }
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
        else -> !classInstantiationInfo[fullName].isNullOrEmpty()
    }

    override fun getExternalCtors(klass: Class): Set<Method> =
        externalConstructors.getOrDefault(klass.fullName, setOf())
            .mapTo(mutableSetOf()) { (klassName, methodName, desc) ->
                klass.cm[klassName].getMethod(methodName, desc)
            }

    override fun get(klass: Class, accessLevel: AccessModifier, random: Random): Class = `try` {
        val concreteClassName = when (val fullName = klass.fullName) {
            in predefinedConcreteInstanceInfo -> predefinedConcreteInstanceInfo.getValue(fullName)
                .filter { isDirectlyInstantiable(klass.cm[it], accessLevel) }
                .random(random)

            else -> classInstantiationInfo.getOrDefault(fullName, setOf())
                .filter { isDirectlyInstantiable(klass.cm[it], accessLevel) }
                .let {
                    if (fullName in it) fullName
                    else it.random(random)
                }
        }
        klass.cm[concreteClassName]
    }.getOrElse {
        throw NoConcreteInstanceException(klass)
    }

    override fun get(klass: Class, accessLevel: AccessModifier, excludes: Set<Class>, random: Random): Class = `try` {
        val excludeNames = excludes.mapTo(mutableSetOf()) { it.fullName }
        val concreteClassName = when (val fullName = klass.fullName) {
            in predefinedConcreteInstanceInfo -> (predefinedConcreteInstanceInfo.getValue(fullName) - excludeNames)
                .filter { isDirectlyInstantiable(klass.cm[it], accessLevel) }
                .random(random)

            else -> (classInstantiationInfo.getOrDefault(fullName, setOf()) - excludeNames)
                .filter { isDirectlyInstantiable(klass.cm[it], accessLevel) }
                .let {
                    if (fullName in it) fullName
                    else it.random(random)
                }
        }
        klass.cm[concreteClassName]
    }.getOrElse {
        throw NoConcreteInstanceException(klass)
    }

    override fun getAllConcreteSubtypes(klass: Class, accessLevel: AccessModifier): Set<Class> =
        when (klass.fullName) {
            in predefinedConcreteInstanceInfo -> predefinedConcreteInstanceInfo.getValue(klass.fullName)
                .filter { isDirectlyInstantiable(klass.cm[it], accessLevel) }
                .mapTo(mutableSetOf()) { klass.cm[it] }

            else -> classInstantiationInfo.getOrDefault(klass.fullName, setOf())
                .mapTo(mutableSetOf()) { klass.cm[it] }
                .filterIsInstance<ConcreteClass>()
                .filterTo(mutableSetOf()) { isDirectlyInstantiable(it, accessLevel) }
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
    private val ctx: ExecutionContext,
    private val baseAccessLevel: AccessModifier = AccessModifier.Private
) : ClassVisitor {
    override val cm: ClassManager get() = ctx.cm

    override fun cleanup() {}

    override fun visit(klass: Class) {
        if (StringClassInstantiationManagerImpl.isDirectlyInstantiable(klass, baseAccessLevel))
            addInstantiableClass(klass, klass)
        super.visit(klass)
    }

    override fun visitMethod(method: Method) {
        val returnType = (method.returnType as? ClassType) ?: return
        if (!baseAccessLevel.canAccess(method.accessModifier)) return
        if (!baseAccessLevel.canAccess(method.klass.accessModifier)) return
        if (!method.isStatic || method.argTypes.any { it.isSubtypeOfCached(returnType) } || method.isSynthetic) return

        addExternalCtor(returnType.klass, method)
    }

    private fun addInstantiableClass(klass: Class, instantiableKlass: Class) {
        for (parent in klass.allAncestors) {
            addInstantiableClass(parent, instantiableKlass)
        }
        StringClassInstantiationManagerImpl[klass] = instantiableKlass
    }

    private fun addExternalCtor(klass: Class, method: Method) {
        for (parent in klass.allAncestors) {
            addExternalCtor(parent, method)
        }
        StringClassInstantiationManagerImpl[klass] = method
    }
}
