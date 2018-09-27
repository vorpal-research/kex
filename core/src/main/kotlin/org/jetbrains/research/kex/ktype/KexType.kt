package org.jetbrains.research.kex.ktype

import org.jetbrains.research.kex.util.defaultHashCode
import org.jetbrains.research.kex.util.log
import org.jetbrains.research.kex.util.unreachable
import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.type.*

val Type.kexType get() = KexType.fromType(this)

fun mergeTypes(vararg types: KexType): KexType = mergeTypes(types.toList())

fun mergeTypes(types: Collection<KexType>): KexType {
    val nonNullTypes = types.filterNot { it is KexNull }
    val uniqueTypes = nonNullTypes.toSet()
    require(uniqueTypes.isNotEmpty()) { log.error("Trying to merge null-only types") }
    return when {
        uniqueTypes.all { it is KexPointer } -> {
            var result = TF.objectType.kexType
            val classes = uniqueTypes.map { it as KexClass }
            for (i in 0..classes.lastIndex) {
                val isAncestor = classes.fold(true) { acc, `class` ->
                    acc && classes[i].`class`.isAncestor(`class`.`class`)
                }

                if (isAncestor) {
                    result = classes[i]
                }
            }
            result
        }
        uniqueTypes.all { it === KexLong } -> KexLong
        uniqueTypes.all { it is KexIntegral } -> uniqueTypes.maxBy { it.bitsize }!!
        uniqueTypes.all { it === KexFloat } -> KexFloat
        uniqueTypes.all { it === KexDouble } -> KexDouble
        else -> unreachable { log.error("Unexpected set of types: $types") }
    }
}

abstract class KexType {
    companion object {
        const val WORD = 32
        const val DWORD = WORD * 2


        fun fromType(type: Type): KexType = when (type) {
            is Integral -> when (type) {
                is BoolType -> KexBool
                is LongType -> KexLong
                else -> KexInt
            }
            is Real -> when (type) {
                is FloatType -> KexFloat
                is DoubleType -> KexDouble
                else -> unreachable { log.error("Unknown real type: $type") }
            }
            is Reference -> when (type) {
                is ClassType -> KexClass(type.`class`)
                is ArrayType -> KexArray(fromType(type.component))
                is NullType -> KexNull
                else -> unreachable { log.error("Unknown reference type: $type") }
            }
            is VoidType -> KexVoid
            else -> unreachable { log.error("Unknown type: $type") }
        }
    }

    abstract val name: String
    abstract val bitsize: Int
    abstract val kfgType: Type

    override fun toString() = name
}

object KexVoid : KexType() {
    override val name: String
        get() = "void"

    override val bitsize: Int
        get() = throw IllegalAccessError("Trying to get bitsize of void")

    override val kfgType: Type
        get() = TF.voidType

    override fun hashCode() = defaultHashCode(name)
    override fun equals(other: Any?) = this === other
}