package org.jetbrains.research.kex.ktype

import kotlinx.serialization.Serializable
import org.jetbrains.research.kex.BaseType
import org.jetbrains.research.kex.InheritanceInfo
import org.jetbrains.research.kex.InheritorOf
import org.jetbrains.research.kex.util.defaultHashCode
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kex.util.unreachable
import org.jetbrains.research.kfg.type.*
import kotlin.reflect.KClass
import org.jetbrains.research.kfg.ir.Class as KfgClass

val Type.kexType get() = KexType.fromType(this)
val KfgClass.kexType get() = KexType.fromClass(this)

fun mergeTypes(tf: TypeFactory, vararg types: KexType): KexType = mergeTypes(tf, types.toList())

fun mergeTypes(tf: TypeFactory, types: Collection<KexType>): KexType {
    val nonNullTypes = types.filterNot { it is KexNull }
    val uniqueTypes = nonNullTypes.toSet()
    require(uniqueTypes.isNotEmpty()) { log.error("Trying to merge null-only types") }
    return when {
        uniqueTypes.all { it is KexPointer } -> {
            var result = tf.objectType.kexType
            val classes = uniqueTypes.map { it as KexClass }.map { tf.getRefType(it.`class`) as ClassType }
            for (i in 0..classes.lastIndex) {
                val isAncestor = classes.fold(true) { acc, `class` ->
                    acc && classes[i].`class`.isAncestor(`class`.`class`)
                }

                if (isAncestor) {
                    result = classes[i].kexType
                }
            }
            result
        }
        uniqueTypes.all { it is KexLong } -> KexLong()
        uniqueTypes.all { it is KexIntegral } -> uniqueTypes.maxBy { it.bitsize }!!
        uniqueTypes.all { it is KexFloat } -> KexFloat()
        uniqueTypes.all { it is KexDouble } -> KexDouble()
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

            inheritanceInfo?.inheritors?.map {
                @Suppress("UNCHECKED_CAST")
                it.name to (loader.loadClass(it.inheritorClass).kotlin as KClass<KexType>)
            }?.toMap() ?: mapOf()
        }

        val reverse = types.map { it.value to it.key }.toMap()

        fun fromType(type: Type): KexType = when (type) {
            is Integral -> when (type) {
                is BoolType -> KexBool()
                is ByteType -> KexByte()
                is ShortType -> KexShort()
                is CharType -> KexChar()
                is LongType -> KexLong()
                else -> KexInt()
            }
            is Real -> when (type) {
                is FloatType -> KexFloat()
                is DoubleType -> KexDouble()
                else -> unreachable { log.error("Unknown real type: $type") }
            }
            is Reference -> when (type) {
                is ClassType -> KexClass(type.`class`.fullname)
                is ArrayType -> KexArray(fromType(type.component))
                is NullType -> KexNull()
                else -> unreachable { log.error("Unknown reference type: $type") }
            }
            is VoidType -> KexVoid()
            else -> unreachable { log.error("Unknown type: $type") }
        }

        fun fromClass(klass: KfgClass) = KexClass(klass.fullname)
    }

    abstract val name: String
    abstract val bitsize: Int

    abstract fun getKfgType(types: TypeFactory): Type

    override fun toString() = name
}

@InheritorOf("KexType")
@Serializable
class KexVoid : KexType() {
    override val name: String
        get() = "void"

    override val bitsize: Int
        get() = throw IllegalAccessError("Trying to get bitsize of void")

    override fun getKfgType(types: TypeFactory): Type = types.voidType

    override fun hashCode() = defaultHashCode(name)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KexVoid) return false
        return true
    }
}