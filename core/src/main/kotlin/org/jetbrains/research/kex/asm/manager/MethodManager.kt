package org.jetbrains.research.kex.asm.manager

import org.jetbrains.research.kex.config.kexConfig
import org.jetbrains.research.kex.ktype.type
import org.jetbrains.research.kex.util.asArray
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
            ignorePackages.addAll(
                kexConfig.getMultipleStringValue("inliner", "ignorePackage", ",").map {
                    Package.parse(it)
                }
            )
            ignoreClasses.addAll(
                kexConfig.getMultipleStringValue("inliner", "ignoreClass", ",").map {
                    it.replace(Package.CANONICAL_SEPARATOR, Package.SEPARATOR)
                }
            )
            ignorePackages += Package.parse("org.jetbrains.research.kex.intrinsics.*")
        }

        fun isIgnored(method: Method) = when {
            ignorePackages.any { it.isParent(method.klass.pkg) } -> true
            ignoreClasses.any { method.cm[it] == method.klass } -> true
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
        private const val assertIntrinsics = "org/jetbrains/research/kex/intrinsics/AssertIntrinsics"
        private const val collectionIntrinsics = "org/jetbrains/research/kex/intrinsics/CollectionIntrinsics"
        private const val unknownIntrinsics = "org/jetbrains/research/kex/intrinsics/UnknownIntrinsics"
        private const val objectIntrinsics = "org/jetbrains/research/kex/intrinsics/ObjectIntrinsics"

        fun assertionsIntrinsics(cm: ClassManager) = cm[assertIntrinsics]
        fun collectionIntrinsics(cm: ClassManager) = cm[collectionIntrinsics]
        fun unknownIntrinsics(cm: ClassManager) = cm[unknownIntrinsics]
        fun objectIntrinsics(cm: ClassManager) = cm[objectIntrinsics]

        /**
         * assert intrinsics
         */
        fun kexAssume(cm: ClassManager) = cm[assertIntrinsics].getMethod(
            "kexAssume",
            MethodDesc(
                arrayOf(cm.type.getArrayType(cm.type.boolType)),
                cm.type.voidType
            )
        )

        fun kexNotNull(cm: ClassManager) = cm[assertIntrinsics].getMethod(
            "kexNotNull",
            MethodDesc(
                arrayOf(cm.type.objectType),
                cm.type.objectType
            )
        )

        fun kexAssert(cm: ClassManager) = cm[assertIntrinsics].getMethod(
            "kexAssert",
            MethodDesc(
                arrayOf(cm.type.getArrayType(cm.type.boolType)),
                cm.type.voidType
            )
        )

        fun kexAssertWithId(cm: ClassManager) = cm[assertIntrinsics].getMethod(
            "kexAssert",
            MethodDesc(
                arrayOf(cm.type.stringType, cm.type.getArrayType(cm.type.boolType)),
                cm.type.voidType
            )
        )

        fun kexUnreachable(cm: ClassManager) = cm[assertIntrinsics].getMethod(
            "kexUnreachable",
            MethodDesc(
                arrayOf(cm.type.getArrayType(cm.type.boolType)),
                cm.type.voidType
            )
        )

        fun kexUnreachableWithId(cm: ClassManager) = cm[assertIntrinsics].getMethod(
            "kexUnreachable",
            MethodDesc(
                arrayOf(cm.type.stringType, cm.type.getArrayType(cm.type.boolType)),
                cm.type.voidType
            )
        )

        /**
         * unknown intrinsics
         */
        fun kexUnknownBoolean(cm: ClassManager) = cm[unknownIntrinsics].getMethod(
            "kexUnknownBoolean",
            MethodDesc(
                arrayOf(),
                cm.type.boolType
            )
        )

        fun kexUnknownByte(cm: ClassManager) = cm[unknownIntrinsics].getMethod(
            "kexUnknownByte",
            MethodDesc(
                arrayOf(),
                cm.type.byteType
            )
        )

        fun kexUnknownChar(cm: ClassManager) = cm[unknownIntrinsics].getMethod(
            "kexUnknownChar",
            MethodDesc(
                arrayOf(),
                cm.type.charType
            )
        )

        fun kexUnknownShort(cm: ClassManager) = cm[unknownIntrinsics].getMethod(
            "kexUnknownShort",
            MethodDesc(
                arrayOf(),
                cm.type.shortType
            )
        )

        fun kexUnknownInt(cm: ClassManager) = cm[unknownIntrinsics].getMethod(
            "kexUnknownInt",
            MethodDesc(
                arrayOf(),
                cm.type.intType
            )
        )

        fun kexUnknownLong(cm: ClassManager) = cm[unknownIntrinsics].getMethod(
            "kexUnknownLong",
            MethodDesc(
                arrayOf(),
                cm.type.longType
            )
        )

        fun kexUnknownFloat(cm: ClassManager) = cm[unknownIntrinsics].getMethod(
            "kexUnknownFloat",
            MethodDesc(
                arrayOf(),
                cm.type.floatType
            )
        )

        fun kexUnknownDouble(cm: ClassManager) = cm[unknownIntrinsics].getMethod(
            "kexUnknownDouble",
            MethodDesc(
                arrayOf(),
                cm.type.doubleType
            )
        )

        fun kexUnknown(cm: ClassManager) = cm[unknownIntrinsics].getMethod(
            "kexUnknown",
            MethodDesc(
                arrayOf(),
                cm.type.objectType
            )
        )


        fun kexUnknownBooleanArray(cm: ClassManager) = cm[unknownIntrinsics].getMethod(
            "kexUnknownBooleanArray",
            MethodDesc(
                arrayOf(),
                cm.type.getArrayType(cm.type.boolType)
            )
        )

        fun kexUnknownByteArray(cm: ClassManager) = cm[unknownIntrinsics].getMethod(
            "kexUnknownByteArray",
            MethodDesc(
                arrayOf(),
                cm.type.getArrayType(cm.type.byteType)
            )
        )

        fun kexUnknownCharArray(cm: ClassManager) = cm[unknownIntrinsics].getMethod(
            "kexUnknownCharArray",
            MethodDesc(
                arrayOf(),
                cm.type.getArrayType(cm.type.charType)
            )
        )

        fun kexUnknownShortArray(cm: ClassManager) = cm[unknownIntrinsics].getMethod(
            "kexUnknownShortArray",
            MethodDesc(
                arrayOf(),
                cm.type.getArrayType(cm.type.shortType)
            )
        )

        fun kexUnknownIntArray(cm: ClassManager) = cm[unknownIntrinsics].getMethod(
            "kexUnknownIntArray",
            MethodDesc(
                arrayOf(),
                cm.type.getArrayType(cm.type.intType)
            )
        )

        fun kexUnknownLongArray(cm: ClassManager) = cm[unknownIntrinsics].getMethod(
            "kexUnknownLongArray",
            MethodDesc(
                arrayOf(),
                cm.type.getArrayType(cm.type.longType)
            )
        )

        fun kexUnknownFloatArray(cm: ClassManager) = cm[unknownIntrinsics].getMethod(
            "kexUnknownFloatArray",
            MethodDesc(
                arrayOf(),
                cm.type.getArrayType(cm.type.floatType)
            )
        )

        fun kexUnknownDoubleArray(cm: ClassManager) = cm[unknownIntrinsics].getMethod(
            "kexUnknownDoubleArray",
            MethodDesc(
                arrayOf(),
                cm.type.getArrayType(cm.type.doubleType)
            )
        )

        fun kexUnknownArray(cm: ClassManager) = cm[unknownIntrinsics].getMethod(
            "kexUnknownArray",
            MethodDesc(
                arrayOf(),
                cm.type.getArrayType(cm.type.objectType)
            )
        )

        /**
         * collection intrinsics
         */
        fun kexForEach(cm: ClassManager) = cm[collectionIntrinsics].getMethod(
            "forEach",
            cm.type.voidType,
            cm.type.intType,
            cm.type.intType,
            cm["org/jetbrains/research/kex/intrinsics/internal/IntConsumer"].type
        )

        fun kexArrayCopy(cm: ClassManager) = cm[collectionIntrinsics].getMethod(
            "arrayCopy",
            cm.type.voidType,
            cm.type.objectType,
            cm.type.intType,
            cm.type.objectType,
            cm.type.intType,
            cm.type.intType
        )

        fun kexContainsBool(cm: ClassManager) = cm[collectionIntrinsics].getMethod(
            "containsBool",
            cm.type.boolType,
            cm.type.boolType.asArray(cm.type),
            cm.type.boolType
        )

        fun kexContainsByte(cm: ClassManager) = cm[collectionIntrinsics].getMethod(
            "containsByte",
            cm.type.boolType,
            cm.type.byteType.asArray(cm.type),
            cm.type.byteType
        )

        fun kexContainsChar(cm: ClassManager) = cm[collectionIntrinsics].getMethod(
            "containsChar",
            cm.type.boolType,
            cm.type.charType.asArray(cm.type),
            cm.type.charType
        )

        fun kexContainsShort(cm: ClassManager) = cm[collectionIntrinsics].getMethod(
            "containsShort",
            cm.type.boolType,
            cm.type.shortType.asArray(cm.type),
            cm.type.shortType
        )

        fun kexContainsInt(cm: ClassManager) = cm[collectionIntrinsics].getMethod(
            "containsInt",
            cm.type.boolType,
            cm.type.intType.asArray(cm.type),
            cm.type.intType
        )

        fun kexContainsLong(cm: ClassManager) = cm[collectionIntrinsics].getMethod(
            "containsLong",
            cm.type.boolType,
            cm.type.longType.asArray(cm.type),
            cm.type.longType
        )

        fun kexContainsFloat(cm: ClassManager) = cm[collectionIntrinsics].getMethod(
            "containsFloat",
            cm.type.boolType,
            cm.type.floatType.asArray(cm.type),
            cm.type.floatType
        )

        fun kexContainsDouble(cm: ClassManager) = cm[collectionIntrinsics].getMethod(
            "containsDouble",
            cm.type.boolType,
            cm.type.doubleType.asArray(cm.type),
            cm.type.doubleType
        )

        fun kexContainsRef(cm: ClassManager) = cm[collectionIntrinsics].getMethod(
            "containsRef",
            cm.type.boolType,
            cm.type.objectType.asArray(cm.type),
            cm.type.objectType
        )

        fun kexContains(cm: ClassManager) = cm[collectionIntrinsics].getMethod(
            "contains",
            cm.type.boolType,
            cm.type.objectType.asArray(cm.type),
            cm.type.objectType
        )

        fun kexContainsMethods(cm: ClassManager) = setOf(
            kexContainsBool(cm),
            kexContainsByte(cm),
            kexContainsChar(cm),
            kexContainsShort(cm),
            kexContainsInt(cm),
            kexContainsLong(cm),
            kexContainsFloat(cm),
            kexContainsDouble(cm),
            kexContainsRef(cm)
        )
    }
}