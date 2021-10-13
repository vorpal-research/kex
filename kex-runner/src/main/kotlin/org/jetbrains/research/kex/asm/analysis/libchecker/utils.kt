package org.jetbrains.research.kex.asm.analysis.libchecker

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.type.TypeFactory
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
        if (this.capacity == IntType.IntCapacity.INT64) TypeFactory(cm).longType
        else TypeFactory(cm).intType
    }
    is FloatType -> {
        if (this.capacity == FloatType.FloatCapacity.FLOAT32) TypeFactory(cm).floatType
        else TypeFactory(cm).doubleType
    }
    is BoolType -> TypeFactory(cm).boolType
    is ArrayType -> {
        TypeFactory(cm).objectArrayClass
    }
    is StringType -> TypeFactory(cm).stringType
    is CharType -> TypeFactory(cm).charType
    is SimpleType -> realType.kfgType(cm)
    is TypeAlias -> originalType.kfgType(cm)
    is EnumLikeSemanticType -> TODO()
    is StructuredType -> TODO()
    is EnumType -> TODO()
    is RealType -> TypeFactory(cm).getRefType(this.fullName)
    is ChildrenType -> TODO()
    is PrimitiveType -> TODO()
}