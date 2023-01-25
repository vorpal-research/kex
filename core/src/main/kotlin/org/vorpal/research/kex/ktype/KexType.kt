package org.vorpal.research.kex.ktype

import kotlinx.serialization.Serializable
import org.vorpal.research.kex.BaseType
import org.vorpal.research.kex.InheritanceInfo
import org.vorpal.research.kex.InheritorOf
import org.vorpal.research.kex.util.getKexRuntime
import org.vorpal.research.kex.util.javaString
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.type.*
import org.vorpal.research.kfg.util.Flags
import org.vorpal.research.kthelper.assert.ktassert
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log
import kotlin.reflect.KClass
import org.vorpal.research.kfg.ir.Class as KfgClass

object KexRtManager {
    private val rt2KexMapping: Map<String, String>
    private val kex2RtMapping: Map<String, String>

    enum class Mode {
        MAP, UNMAP
    }

    init {
        val kexRt = getKexRuntime()
        val klasses = kexRt?.parse(Flags.readAll, failOnError = true) ?: mapOf()
        rt2KexMapping = klasses.keys.associateBy { it.removePrefix("kex/") }
        kex2RtMapping = klasses.keys.associateWith { it.removePrefix("kex/") }
    }

    val String.rtMapped get() = rt2KexMapping.getOrDefault(this, this)
    val String.rtUnmapped get() = kex2RtMapping.getOrDefault(this, this)

    val KfgClass.rtMapped get() = cm[rt2KexMapping.getOrDefault(fullName, fullName)]
    val Type.rtMapped: Type get() = when (this) {
        is ClassType -> this.klass.rtMapped.type
        is ArrayType -> ArrayType(component.rtMapped)
        else -> this
    }

    val KfgClass.rtUnmapped get() = cm[kex2RtMapping.getOrDefault(fullName, fullName)]
    val Type.rtUnmapped: Type get() = when (this) {
        is ClassType -> this.klass.rtUnmapped.type
        is ArrayType -> ArrayType(component.rtUnmapped)
        else -> this
    }

    val KexType.rtMapped: KexType get() = when (this) {
        is KexClass -> KexClass(rt2KexMapping.getOrDefault(klass, klass))
        is KexReference -> KexReference(reference.rtMapped)
        is KexArray -> KexArray(element.rtMapped)
        else -> this
    }

    val KexType.rtUnmapped: KexType get() = when (this) {
        is KexClass -> KexClass(kex2RtMapping.getOrDefault(klass, klass))
        is KexReference -> KexReference(reference.rtUnmapped)
        is KexArray -> KexArray(element.rtUnmapped)
        else -> this
    }


    val KfgClass.isKexRt get() = fullName in kex2RtMapping
    @Suppress("unused")
    val Type.isKexRt: Boolean get() = when (this) {
        is ClassType -> this.klass.isKexRt
        is ArrayType -> component.isKexRt
        else -> false
    }
    val KexType.isKexRt: Boolean get() = when (this) {
        is KexClass -> klass in kex2RtMapping
        is KexReference -> reference.isKexRt
        is KexArray -> element.isKexRt
        else -> false
    }


    val KfgClass.isJavaRt get() = rt2KexMapping.any { fullName.startsWith(it.key) }
    @Suppress("unused")
    val Type.isJavaRt: Boolean get() = when (this) {
        is ClassType -> this.klass.isJavaRt
        is ArrayType -> component.isJavaRt
        else -> false
    }
    val KexType.isJavaRt: Boolean get() = when (this) {
        is KexClass -> rt2KexMapping.any { klass.startsWith(it.key) }
        is KexReference -> reference.isJavaRt
        is KexArray -> element.isJavaRt
        else -> false
    }

    val Method.isKexRt: Boolean get() = klass.isKexRt
}

val Type.kexType get() = KexType.fromType(this)
val KfgClass.kexType get() = KexType.fromClass(this)
val KfgClass.type get() = this.cm.type.getRefType(this.fullName)

private val KexInteger.actualBitSize
    get() = when (this) {
        is KexBool -> 1
        is KexByte -> 8
        is KexChar -> 8
        is KexShort -> 16
        is KexInt -> 32
        is KexLong -> 64
    }

fun mergeTypes(tf: TypeFactory, vararg types: KexType): KexType = mergeTypes(tf, types.toList())

fun mergeTypes(tf: TypeFactory, types: Collection<KexType>): KexType {
    val uniqueTypes = types.filterNotTo(mutableSetOf()) { it is KexNull }
    ktassert(uniqueTypes.isNotEmpty()) { log.error("Trying to merge null-only types") }
    return when {
        uniqueTypes.size == 1 -> uniqueTypes.first()
        uniqueTypes.all { it is KexPointer } -> {
            var result = tf.objectType.kexType
            val classes = uniqueTypes.map { it as KexClass }.map { tf.getRefType(it.klass) as ClassType }
            for (i in 0..classes.lastIndex) {
                val isAncestor = classes.fold(true) { acc, `class` ->
                    acc && classes[i].klass.isAncestorOf(`class`.klass)
                }

                if (isAncestor) {
                    result = classes[i].kexType
                }
            }
            result
        }

        uniqueTypes.all { it is KexLong } -> KexLong
        uniqueTypes.all { it is KexInteger } -> uniqueTypes.maxByOrNull { (it as KexInteger).actualBitSize }!!
        uniqueTypes.all { it is KexFloat } -> KexFloat
        uniqueTypes.all { it is KexReal } -> KexDouble
        uniqueTypes.all { it is KexInteger || it is KexFloat } -> KexFloat
        uniqueTypes.all { it is KexInteger || it is KexReal } -> KexDouble
        else -> unreachable { log.error("Unexpected set of types: $types") }
    }
}

@BaseType("KexType")
@Serializable
abstract class KexType {
    companion object {
        const val WORD = 32
        const val DWORD = WORD * 2

        val types = run {
            val loader = Thread.currentThread().contextClassLoader
            val resource = loader.getResourceAsStream("KexType.json")
            val inheritanceInfo = resource?.use {
                InheritanceInfo.fromJson(it.bufferedReader().readText())
            }

            inheritanceInfo?.inheritors?.associate {
                @Suppress("UNCHECKED_CAST")
                it.name to (loader.loadClass(it.inheritorClass).kotlin as KClass<KexType>)
            } ?: mapOf()
        }

        val reverse = types.map { it.value to it.key }.toMap()

        fun fromType(type: Type): KexType = when (type) {
            is Integer -> when (type) {
                is BoolType -> KexBool
                is ByteType -> KexByte
                is ShortType -> KexShort
                is CharType -> KexChar
                is LongType -> KexLong
                else -> KexInt
            }
            is Real -> when (type) {
                is FloatType -> KexFloat
                is DoubleType -> KexDouble
                else -> unreachable { log.error("Unknown real type: $type") }
            }
            is Reference -> when (type) {
                is ClassType -> KexClass(type.klass.fullName)
                is ArrayType -> KexArray(fromType(type.component))
                is NullType -> KexNull()
                else -> unreachable { log.error("Unknown reference type: $type") }
            }
            is VoidType -> KexVoid
            else -> unreachable { log.error("Unknown type: $type") }
        }

        fun fromClass(klass: KfgClass) = KexClass(klass.fullName)
    }

    abstract val name: String
    val javaName get() = name.javaString
    abstract val bitSize: Int

    abstract fun getKfgType(types: TypeFactory): Type
    fun isSubtypeOf(tf: TypeFactory, other: KexType) = getKfgType(tf).isSubtypeOf(other.getKfgType(tf))

    override fun toString() = name
}

@InheritorOf("KexType")
@Serializable
object KexVoid : KexType() {
    override val name: String
        get() = "void"

    override val bitSize: Int
        get() = throw IllegalAccessError("Trying to get bit size of void")

    override fun getKfgType(types: TypeFactory): Type = types.voidType

    override fun hashCode() = name.hashCode()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KexVoid) return false
        return true
    }
}

fun KexType.unMemspaced() = when (this) {
    is KexPointer -> withoutMemspace()
    else -> this
}
