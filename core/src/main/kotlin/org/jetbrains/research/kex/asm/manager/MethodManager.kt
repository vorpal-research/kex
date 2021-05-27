package org.jetbrains.research.kex.asm.manager

import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.MethodDesc
import org.jetbrains.research.kfg.ir.value.instruction.ReturnInst

object MethodManager {
    object InlineManager {
        val inliningEnabled = kexConfig.getBooleanValue("inliner", "enabled", true)
        private val ignorePackages = hashSetOf<Package>()
        private val ignoreClasses = hashSetOf<String>()

        init {
            val ignores = kexConfig.getMultipleStringValue("inliner", "ignore", ",")
                    .map { it.replace(".", "/") }
            for (name in ignores) {
                when {
                    name.endsWith("*") -> ignorePackages.add(Package(name))
                    else -> ignoreClasses.add(name)
                }
            }
        }

        fun isIgnored(method: Method) = when {
            ignorePackages.any { it.isParent(method.klass.pkg) } -> true
            ignoreClasses.any { method.cm[it] == method.klass } -> true
            method in KexIntrinsicManager.getNotInlinableMethods(method.cm) -> true
            else -> false
        }

        fun isInlinable(method: Method): Boolean = when {
            !inliningEnabled -> false
            isIgnored(method) -> false
            method.isStatic -> true
            method.isConstructor -> true
            !method.isFinal -> false
            method.flatten().all { it !is ReturnInst } -> false
            else -> true
        }
    }

    object IntrinsicManager {
        private const val intrinsicsClass = "kotlin/jvm/internal/Intrinsics"

        fun checkParameterIsNotNull(cm: ClassManager) = cm[intrinsicsClass].getMethod(
                "checkParameterIsNotNull",
                MethodDesc(
                        arrayOf(cm.type.objectType, cm.type.stringType),
                        cm.type.voidType
                )
        )

        fun checkNotNullParameter(cm: ClassManager) = cm[intrinsicsClass].getMethod(
                "checkNotNullParameter",
                MethodDesc(
                        arrayOf(cm.type.objectType, cm.type.stringType),
                        cm.type.voidType
                )
        )

        fun areEqual(cm: ClassManager) = cm[intrinsicsClass].getMethod(
                "areEqual",
                MethodDesc(
                        arrayOf(cm.type.objectType, cm.type.objectType),
                        cm.type.boolType
                )
        )

    }

    object KexIntrinsicManager {
        private const val intrinsicsClass = "org/jetbrains/research/kex/Intrinsics"
        private const val objectsClass = "org/jetbrains/research/kex/Objects"

        fun getNotInlinableMethods(cm: ClassManager) = setOf(
            kexUnknownBoolean(cm),
            kexUnknownByte(cm),
            kexUnknownChar(cm),
            kexUnknownShort(cm),
            kexUnknownInt(cm),
            kexUnknownLong(cm),
            kexUnknownFloat(cm),
            kexUnknownDouble(cm),
            kexUnknown(cm),

            kexUnknownBooleanArray(cm),
            kexUnknownByteArray(cm),
            kexUnknownCharArray(cm),
            kexUnknownShortArray(cm),
            kexUnknownIntArray(cm),
            kexUnknownLongArray(cm),
            kexUnknownFloatArray(cm),
            kexUnknownDoubleArray(cm),
            kexUnknownArray(cm)
        )

        fun kexAssume(cm: ClassManager) = cm[intrinsicsClass].getMethod(
            "kexAssume",
            MethodDesc(
                arrayOf(cm.type.getArrayType(cm.type.boolType)),
                cm.type.voidType
            )
        )

        fun kexNotNull(cm: ClassManager) = cm[intrinsicsClass].getMethod(
            "kexNotNull",
            MethodDesc(
                arrayOf(cm.type.objectType),
                cm.type.objectType
            )
        )

        fun kexAssert(cm: ClassManager) = cm[intrinsicsClass].getMethod(
            "kexAssert",
            MethodDesc(
                arrayOf(cm.type.getArrayType(cm.type.boolType)),
                cm.type.voidType
            )
        )

        fun kexAssertWithId(cm: ClassManager) = cm[intrinsicsClass].getMethod(
            "kexAssert",
            MethodDesc(
                arrayOf(cm.type.stringType, cm.type.getArrayType(cm.type.boolType)),
                cm.type.voidType
            )
        )

        fun kexUnreachable(cm: ClassManager) = cm[intrinsicsClass].getMethod(
            "kexUnreachable",
            MethodDesc(
                arrayOf(cm.type.getArrayType(cm.type.boolType)),
                cm.type.voidType
            )
        )

        fun kexUnreachableWithId(cm: ClassManager) = cm[intrinsicsClass].getMethod(
            "kexUnreachable",
            MethodDesc(
                arrayOf(cm.type.stringType, cm.type.getArrayType(cm.type.boolType)),
                cm.type.voidType
            )
        )

        fun kexUnknownBoolean(cm: ClassManager) = cm[objectsClass].getMethod(
            "kexUnknownBoolean",
            MethodDesc(
                arrayOf(),
                cm.type.boolType
            )
        )

        fun kexUnknownByte(cm: ClassManager) = cm[objectsClass].getMethod(
            "kexUnknownByte",
            MethodDesc(
                arrayOf(),
                cm.type.byteType
            )
        )

        fun kexUnknownChar(cm: ClassManager) = cm[objectsClass].getMethod(
            "kexUnknownChar",
            MethodDesc(
                arrayOf(),
                cm.type.charType
            )
        )

        fun kexUnknownShort(cm: ClassManager) = cm[objectsClass].getMethod(
            "kexUnknownShort",
            MethodDesc(
                arrayOf(),
                cm.type.shortType
            )
        )

        fun kexUnknownInt(cm: ClassManager) = cm[objectsClass].getMethod(
            "kexUnknownInt",
            MethodDesc(
                arrayOf(),
                cm.type.intType
            )
        )

        fun kexUnknownLong(cm: ClassManager) = cm[objectsClass].getMethod(
            "kexUnknownLong",
            MethodDesc(
                arrayOf(),
                cm.type.longType
            )
        )

        fun kexUnknownFloat(cm: ClassManager) = cm[objectsClass].getMethod(
            "kexUnknownFloat",
            MethodDesc(
                arrayOf(),
                cm.type.floatType
            )
        )

        fun kexUnknownDouble(cm: ClassManager) = cm[objectsClass].getMethod(
            "kexUnknownDouble",
            MethodDesc(
                arrayOf(),
                cm.type.doubleType
            )
        )

        fun kexUnknown(cm: ClassManager) = cm[objectsClass].getMethod(
            "kexUnknown",
            MethodDesc(
                arrayOf(),
                cm.type.objectType
            )
        )


        fun kexUnknownBooleanArray(cm: ClassManager) = cm[objectsClass].getMethod(
            "kexUnknownBooleanArray",
            MethodDesc(
                arrayOf(),
                cm.type.getArrayType(cm.type.boolType)
            )
        )

        fun kexUnknownByteArray(cm: ClassManager) = cm[objectsClass].getMethod(
            "kexUnknownByteArray",
            MethodDesc(
                arrayOf(),
                cm.type.getArrayType(cm.type.byteType)
            )
        )

        fun kexUnknownCharArray(cm: ClassManager) = cm[objectsClass].getMethod(
            "kexUnknownCharArray",
            MethodDesc(
                arrayOf(),
                cm.type.getArrayType(cm.type.charType)
            )
        )

        fun kexUnknownShortArray(cm: ClassManager) = cm[objectsClass].getMethod(
            "kexUnknownShortArray",
            MethodDesc(
                arrayOf(),
                cm.type.getArrayType(cm.type.shortType)
            )
        )

        fun kexUnknownIntArray(cm: ClassManager) = cm[objectsClass].getMethod(
            "kexUnknownIntArray",
            MethodDesc(
                arrayOf(),
                cm.type.getArrayType(cm.type.intType)
            )
        )

        fun kexUnknownLongArray(cm: ClassManager) = cm[objectsClass].getMethod(
            "kexUnknownLongArray",
            MethodDesc(
                arrayOf(),
                cm.type.getArrayType(cm.type.longType)
            )
        )

        fun kexUnknownFloatArray(cm: ClassManager) = cm[objectsClass].getMethod(
            "kexUnknownFloatArray",
            MethodDesc(
                arrayOf(),
                cm.type.getArrayType(cm.type.floatType)
            )
        )

        fun kexUnknownDoubleArray(cm: ClassManager) = cm[objectsClass].getMethod(
            "kexUnknownDoubleArray",
            MethodDesc(
                arrayOf(),
                cm.type.getArrayType(cm.type.doubleType)
            )
        )

        fun kexUnknownArray(cm: ClassManager) = cm[objectsClass].getMethod(
            "kexUnknownArray",
            MethodDesc(
                arrayOf(),
                cm.type.getArrayType(cm.type.objectType)
            )
        )

    }
}