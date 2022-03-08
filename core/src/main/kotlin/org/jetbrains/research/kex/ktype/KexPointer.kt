package org.jetbrains.research.kex.ktype

import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.InheritorOf
import org.jetbrains.research.kfg.type.SystemTypeNames
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.type.TypeFactory
import org.jetbrains.research.kthelper.defaultHashCode

@InheritorOf("KexType")
@Serializable
sealed class KexPointer : KexType() {
    abstract val memspace: Int

    companion object {
        const val defaultMemspace = 0
    }

    override val bitSize: Int
        get() = WORD

    abstract fun withMemspace(memspace: Int): KexPointer
    fun withoutMemspace() = withMemspace(defaultMemspace)
}

@InheritorOf("KexType")
@Serializable
class KexClass(val klass: String, override val memspace: Int = defaultMemspace) : KexPointer() {
    override val name: String
        get() = klass

    val canonicalDesc get() = name.replace("/", ".")

    fun kfgClass(types: TypeFactory) = types.cm[klass]

    override fun getKfgType(types: TypeFactory): Type = types.getRefType(klass)

    override fun withMemspace(memspace: Int) = KexClass(klass, memspace)

    override fun hashCode() = defaultHashCode(klass)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KexClass

        return klass == other.klass && memspace == other.memspace
    }
}

@InheritorOf("KexType")
@Serializable
class KexArray(
        val element: KexType,
        override val memspace: Int = defaultMemspace) : KexPointer() {
    override val name: String
        get() = "$element[]"

    override fun getKfgType(types: TypeFactory): Type = types.getArrayType(element.getKfgType(types))

    override fun withMemspace(memspace: Int) = KexArray(element, memspace)

    override fun hashCode() = defaultHashCode(element)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KexArray

        return element == other.element && memspace == other.memspace
    }
}

@InheritorOf("KexType")
@Serializable
class KexReference(
        val reference: KexType,
        override val memspace: Int = defaultMemspace) : KexPointer() {
    override val name: String
        get() = "&($reference)"

    override fun getKfgType(types: TypeFactory): Type = reference.getKfgType(types)

    override fun withMemspace(memspace: Int) = KexReference(reference, memspace)

    override fun hashCode() = defaultHashCode(reference)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KexReference

        return reference == other.reference && memspace == other.memspace
    }
}

@InheritorOf("KexType")
@Serializable
class KexNull : KexPointer() {
    override val name: String
        get() = "null"

    override val memspace = defaultMemspace

    override fun getKfgType(types: TypeFactory): Type = types.nullType

    override fun withMemspace(memspace: Int) = this

    override fun hashCode() = defaultHashCode(name)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KexNull) return false
        return true
    }
}

fun KexType.unreferenced(): KexType = when (this) {
    is KexReference -> this.reference.unreferenced()
    else -> this
}

fun KexType.asArray() = KexArray(this)
val KexType.isArray get() = this is KexArray
fun KexString() = KexClass(SystemTypeNames.stringClass)
val KexType.isString get() = this is KexClass && this.klass == SystemTypeNames.stringClass
fun KexJavaClass() = KexClass(SystemTypeNames.classClass)

val KexType.isJavaClass get() = this is KexClass && this.klass == SystemTypeNames.classClass