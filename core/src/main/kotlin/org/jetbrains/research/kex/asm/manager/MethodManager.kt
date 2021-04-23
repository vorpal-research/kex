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
        private val ignoreMethods = hashSetOf<Method>()

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
            ignorePackages.any { it.isParent(method.`class`.`package`) } -> true
            ignoreClasses.any { method.cm[it] == method.`class` } -> true
            ignoreMethods.contains(method) -> true
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

    }
}