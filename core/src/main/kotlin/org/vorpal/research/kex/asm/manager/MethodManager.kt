package org.vorpal.research.kex.asm.manager

import org.vorpal.research.kex.asm.util.AccessModifier
import org.vorpal.research.kex.asm.util.accessModifier
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.util.KfgTargetFilter
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.instruction.ReturnInst
import org.vorpal.research.kfg.type.objectType
import org.vorpal.research.kfg.type.stringType
import org.vorpal.research.kthelper.assert.ktassert
import org.vorpal.research.kthelper.logging.log

@Suppress("unused", "MemberVisibilityCanBePrivate")
object MethodManager {

    fun canBeImpacted(method: Method, accessLevel: AccessModifier): Boolean = when {
        method.isAbstract -> false
        method.isStaticInitializer -> false
        method.klass.isSynthetic -> false
        method.klass.isAbstract && method.isConstructor -> false
        method.isSynthetic -> false
        !accessLevel.canAccess(method.klass.accessModifier) -> false
        !accessLevel.canAccess(method.accessModifier) -> false
        else -> true
    }

    object InlineManager {
        val inliningEnabled = kexConfig.getBooleanValue("inliner", "enabled", true)
        private val ignores = hashSetOf<KfgTargetFilter>()

        init {
            ignores.addAll(
                kexConfig.getMultipleStringValue("inliner", "ignore", ",").map {
                    KfgTargetFilter.parse(it)
                }
            )
            ignores += KfgTargetFilter.parse("package org.vorpal.research.kex.intrinsics.*")
        }

        fun isIgnored(method: Method) = when {
            ignores.any { it.matches(method.klass) } -> true
            else -> false
        }

        fun isInlinable(method: Method): Boolean = when {
            !inliningEnabled -> false
            isIgnored(method) -> false
            method.isStatic -> true
            method.isConstructor -> true
            !method.isFinal -> false
            method.body.flatten().all { it !is ReturnInst } -> false
            else -> true
        }
    }

    object IntrinsicManager {
        private const val intrinsicsClass = "kotlin/jvm/internal/Intrinsics"
        private const val objectsClass = "java/util/Objects"

        fun checkParameterIsNotNull(cm: ClassManager) = synchronized(KexIntrinsicManager) {
            cm[intrinsicsClass].getMethod(
                "checkParameterIsNotNull",
                cm.type.voidType, cm.type.objectType, cm.type.stringType
            )
        }

        fun checkNotNullParameter(cm: ClassManager) = synchronized(KexIntrinsicManager) {
            cm[intrinsicsClass].getMethod(
                "checkNotNullParameter",
                cm.type.voidType, cm.type.objectType, cm.type.stringType
            )
        }

        fun areEqual(cm: ClassManager) = synchronized(KexIntrinsicManager) {
            cm[intrinsicsClass].getMethod(
                "areEqual",
                cm.type.boolType, cm.type.objectType, cm.type.objectType
            )
        }

        fun requireNonNull(cm: ClassManager) = synchronized(KexIntrinsicManager) {
            cm[objectsClass].getMethod("requireNonNull", cm.type.objectType, cm.type.objectType, cm.type.stringType)
        }
    }

    object KexIntrinsicManager {
        private val supportedVersions = mutableSetOf("0.1.0")
        private const val assertIntrinsics = "org/vorpal/research/kex/intrinsics/AssertIntrinsics"
        private const val collectionIntrinsics = "org/vorpal/research/kex/intrinsics/CollectionIntrinsics"
        private const val unknownIntrinsics = "org/vorpal/research/kex/intrinsics/UnknownIntrinsics"
        private const val objectIntrinsics = "org/vorpal/research/kex/intrinsics/ObjectIntrinsics"

        init {
            val currentVersion = kexConfig.getStringValue("kex", "intrinsicsVersion")
            ktassert(currentVersion in supportedVersions) {
                log.error("Unsupported version of kex-intrinsics: $currentVersion")
                log.error("Supported versions: ${supportedVersions.joinToString(", ")}")
            }
        }

        fun assertionsIntrinsics(cm: ClassManager) = cm[assertIntrinsics]
        fun collectionIntrinsics(cm: ClassManager) = cm[collectionIntrinsics]
        fun unknownIntrinsics(cm: ClassManager) = cm[unknownIntrinsics]
        fun objectIntrinsics(cm: ClassManager) = cm[objectIntrinsics]
        private fun getGenerator(cm: ClassManager, name: String) =
            cm["org/vorpal/research/kex/intrinsics/internal/${name}Generator"]

        /**
         * assert intrinsics
         */
        fun kexAssume(cm: ClassManager) = cm[assertIntrinsics].getMethod(
            "kexAssume",
            cm.type.voidType, cm.type.boolType
        )

        fun kexNotNull(cm: ClassManager) = cm[assertIntrinsics].getMethod(
            "kexNotNull",
            cm.type.objectType, cm.type.objectType
        )

        fun kexAssert(cm: ClassManager) = cm[assertIntrinsics].getMethod(
            "kexAssert",
            cm.type.voidType, cm.type.boolType
        )

        fun kexAssertWithId(cm: ClassManager) = cm[assertIntrinsics].getMethod(
            "kexAssert",
            cm.type.voidType, cm.type.stringType, cm.type.boolType
        )

        fun kexUnreachableEmpty(cm: ClassManager) = cm[assertIntrinsics].getMethod(
            "kexUnreachable",
            cm.type.voidType,
        )

        fun kexUnreachable(cm: ClassManager) = cm[assertIntrinsics].getMethod(
            "kexUnreachable",
            cm.type.voidType, cm.type.boolType
        )

        fun kexUnreachableWithId(cm: ClassManager) = cm[assertIntrinsics].getMethod(
            "kexUnreachable",
            cm.type.voidType, cm.type.stringType, cm.type.boolType
        )

        /**
         * unknown intrinsics
         */
        fun kexUnknownBoolean(cm: ClassManager) = cm[unknownIntrinsics].getMethod(
            "kexUnknownBoolean",
            cm.type.boolType,
        )

        fun kexUnknownByte(cm: ClassManager) = cm[unknownIntrinsics].getMethod(
            "kexUnknownByte",
            cm.type.byteType,
        )

        fun kexUnknownChar(cm: ClassManager) = cm[unknownIntrinsics].getMethod(
            "kexUnknownChar",
            cm.type.charType,
        )

        fun kexUnknownShort(cm: ClassManager) = cm[unknownIntrinsics].getMethod(
            "kexUnknownShort",
            cm.type.shortType,
        )

        fun kexUnknownInt(cm: ClassManager) = cm[unknownIntrinsics].getMethod(
            "kexUnknownInt",
            cm.type.intType,
        )

        fun kexUnknownLong(cm: ClassManager) = cm[unknownIntrinsics].getMethod(
            "kexUnknownLong",
            cm.type.longType,
        )

        fun kexUnknownFloat(cm: ClassManager) = cm[unknownIntrinsics].getMethod(
            "kexUnknownFloat",
            cm.type.floatType,
        )

        fun kexUnknownDouble(cm: ClassManager) = cm[unknownIntrinsics].getMethod(
            "kexUnknownDouble",
            cm.type.doubleType,
        )

        fun kexUnknown(cm: ClassManager) = cm[unknownIntrinsics].getMethod(
            "kexUnknown",
            cm.type.objectType,
        )


        fun kexUnknownBooleanArray(cm: ClassManager) = cm[unknownIntrinsics].getMethod(
            "kexUnknownBooleanArray",
            cm.type.getArrayType(cm.type.boolType),
        )

        fun kexUnknownByteArray(cm: ClassManager) = cm[unknownIntrinsics].getMethod(
            "kexUnknownByteArray",
            cm.type.getArrayType(cm.type.byteType),
        )

        fun kexUnknownCharArray(cm: ClassManager) = cm[unknownIntrinsics].getMethod(
            "kexUnknownCharArray",
            cm.type.getArrayType(cm.type.charType),
        )

        fun kexUnknownShortArray(cm: ClassManager) = cm[unknownIntrinsics].getMethod(
            "kexUnknownShortArray",
            cm.type.getArrayType(cm.type.shortType),
        )

        fun kexUnknownIntArray(cm: ClassManager) = cm[unknownIntrinsics].getMethod(
            "kexUnknownIntArray",
            cm.type.getArrayType(cm.type.intType),
        )

        fun kexUnknownLongArray(cm: ClassManager) = cm[unknownIntrinsics].getMethod(
            "kexUnknownLongArray",
            cm.type.getArrayType(cm.type.longType),
        )

        fun kexUnknownFloatArray(cm: ClassManager) = cm[unknownIntrinsics].getMethod(
            "kexUnknownFloatArray",
            cm.type.getArrayType(cm.type.floatType),
        )

        fun kexUnknownDoubleArray(cm: ClassManager) = cm[unknownIntrinsics].getMethod(
            "kexUnknownDoubleArray",
            cm.type.getArrayType(cm.type.doubleType),
        )

        fun kexUnknownArray(cm: ClassManager) = cm[unknownIntrinsics].getMethod(
            "kexUnknownArray",
            cm.type.getArrayType(cm.type.objectType),
        )

        /**
         * collection intrinsics
         */
        fun kexForAll(cm: ClassManager) = cm[collectionIntrinsics].getMethod(
            "forAll",
            cm.type.boolType,
            cm.type.intType,
            cm.type.intType,
            cm["org/vorpal/research/kex/intrinsics/internal/IntConsumer"].asType
        )

        fun kexContainsBool(cm: ClassManager) = cm[collectionIntrinsics].getMethod(
            "containsBool",
            cm.type.boolType,
            cm.type.boolType.asArray,
            cm.type.boolType
        )

        fun kexContainsByte(cm: ClassManager) = cm[collectionIntrinsics].getMethod(
            "containsByte",
            cm.type.boolType,
            cm.type.byteType.asArray,
            cm.type.byteType
        )

        fun kexContainsChar(cm: ClassManager) = cm[collectionIntrinsics].getMethod(
            "containsChar",
            cm.type.boolType,
            cm.type.charType.asArray,
            cm.type.charType
        )

        fun kexContainsShort(cm: ClassManager) = cm[collectionIntrinsics].getMethod(
            "containsShort",
            cm.type.boolType,
            cm.type.shortType.asArray,
            cm.type.shortType
        )

        fun kexContainsInt(cm: ClassManager) = cm[collectionIntrinsics].getMethod(
            "containsInt",
            cm.type.boolType,
            cm.type.intType.asArray,
            cm.type.intType
        )

        fun kexContainsLong(cm: ClassManager) = cm[collectionIntrinsics].getMethod(
            "containsLong",
            cm.type.boolType,
            cm.type.longType.asArray,
            cm.type.longType
        )

        fun kexContainsFloat(cm: ClassManager) = cm[collectionIntrinsics].getMethod(
            "containsFloat",
            cm.type.boolType,
            cm.type.floatType.asArray,
            cm.type.floatType
        )

        fun kexContainsDouble(cm: ClassManager) = cm[collectionIntrinsics].getMethod(
            "containsDouble",
            cm.type.boolType,
            cm.type.doubleType.asArray,
            cm.type.doubleType
        )

        fun kexContainsRef(cm: ClassManager) = cm[collectionIntrinsics].getMethod(
            "containsRef",
            cm.type.boolType,
            cm.type.objectType.asArray,
            cm.type.objectType
        )

        fun kexContains(cm: ClassManager) = cm[collectionIntrinsics].getMethod(
            "contains",
            cm.type.boolType,
            cm.type.objectType.asArray,
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

        fun kexGenerateBoolArray(cm: ClassManager) = cm[collectionIntrinsics].getMethod(
            "generateBoolArray",
            cm.type.boolType.asArray,
            cm.type.intType,
            getGenerator(cm, "Boolean").asType
        )

        fun kexGenerateByteArray(cm: ClassManager) = cm[collectionIntrinsics].getMethod(
            "generateByteArray",
            cm.type.byteType.asArray,
            cm.type.intType,
            getGenerator(cm, "Byte").asType
        )

        fun kexGenerateCharArray(cm: ClassManager) = cm[collectionIntrinsics].getMethod(
            "generateCharArray",
            cm.type.charType.asArray,
            cm.type.intType,
            getGenerator(cm, "Char").asType
        )

        fun kexGenerateShortArray(cm: ClassManager) = cm[collectionIntrinsics].getMethod(
            "generateShortArray",
            cm.type.shortType.asArray,
            cm.type.intType,
            getGenerator(cm, "Short").asType
        )

        fun kexGenerateIntArray(cm: ClassManager) = cm[collectionIntrinsics].getMethod(
            "generateIntArray",
            cm.type.intType.asArray,
            cm.type.intType,
            getGenerator(cm, "Int").asType
        )

        fun kexGenerateLongArray(cm: ClassManager) = cm[collectionIntrinsics].getMethod(
            "generateLongArray",
            cm.type.longType.asArray,
            cm.type.intType,
            getGenerator(cm, "Long").asType
        )

        fun kexGenerateFloatArray(cm: ClassManager) = cm[collectionIntrinsics].getMethod(
            "generateFloatArray",
            cm.type.floatType.asArray,
            cm.type.intType,
            getGenerator(cm, "Float").asType
        )

        fun kexGenerateDoubleArray(cm: ClassManager) = cm[collectionIntrinsics].getMethod(
            "generateDoubleArray",
            cm.type.doubleType.asArray,
            cm.type.intType,
            getGenerator(cm, "Double").asType
        )

        fun kexGenerateObjectArray(cm: ClassManager) = cm[collectionIntrinsics].getMethod(
            "generateObjectArray",
            cm.type.objectType.asArray,
            cm.type.intType,
            getGenerator(cm, "Object").asType
        )

        fun kexGenerateArrayMethods(cm: ClassManager) = setOf(
            kexGenerateBoolArray(cm),
            kexGenerateByteArray(cm),
            kexGenerateCharArray(cm),
            kexGenerateShortArray(cm),
            kexGenerateIntArray(cm),
            kexGenerateLongArray(cm),
            kexGenerateFloatArray(cm),
            kexGenerateDoubleArray(cm),
            kexGenerateObjectArray(cm)
        )

        fun kexEquals(cm: ClassManager) = cm[objectIntrinsics].getMethod(
            "equals",
            cm.type.boolType,
            cm.type.objectType,
            cm.type.objectType
        )
    }
}
