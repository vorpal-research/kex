package org.jetbrains.research.kex.asm.analysis.libchecker

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.libsl.asg.*

val Type.asmDescriptor: String
    get() = when(this) {
        is IntType -> {
            if (this.capacity == IntType.IntCapacity.INT64) "J"
            else "I"
        }
        is FloatType -> {
            if (this.capacity == FloatType.FloatCapacity.FLOAT32) "F"
            else "D"
        }
        is BoolType -> "Z"
        is ArrayType -> {
            "[" + this.generic.asmDescriptor
        }
        is CharType -> "C"
        else -> error("unknown type: $name")
    }

fun Type.kfgType(cm: ClassManager): org.jetbrains.research.kfg.type.Type = when(this) {
    is IntType -> {
        if (this.capacity == IntType.IntCapacity.INT64) cm.type.longType
        else cm.type.intType
    }
    is FloatType -> {
        if (this.capacity == FloatType.FloatCapacity.FLOAT32) cm.type.floatType
        else cm.type.doubleType
    }
    is BoolType -> cm.type.boolType
    is ArrayType -> {
        cm.type.objectArrayClass
    }
    is StringType -> cm.type.stringType
    is CharType -> cm.type.charType
    is SimpleType -> realType.kfgType(cm)
    is TypeAlias -> originalType.kfgType(cm)
    is EnumLikeSemanticType -> TODO()
    is StructuredType -> TODO()
    is EnumType -> TODO()
    is RealType -> cm.type.getRefType(this.fullName)
    is ChildrenType -> TODO()
    is PrimitiveType -> TODO()
}