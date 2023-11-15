@file:Suppress("unused")

package org.vorpal.research.kex.asm.util

import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kex.util.isPrivate
import org.vorpal.research.kex.util.isProtected
import org.vorpal.research.kex.util.isPublic
import org.vorpal.research.kex.util.toKfgType
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.type.ArrayType
import org.vorpal.research.kfg.type.ClassType
import org.vorpal.research.kfg.type.TypeFactory
import java.lang.Class
import java.lang.reflect.Field
import java.lang.reflect.Method
import org.vorpal.research.kfg.Package as KfgPackage
import org.vorpal.research.kfg.ir.Class as KfgClass
import org.vorpal.research.kfg.ir.Field as KfgField
import org.vorpal.research.kfg.ir.Method as KfgMethod
import org.vorpal.research.kfg.type.Type as KfgType

enum class Visibility {
    PRIVATE,
    PROTECTED,
    PACKAGE,
    PUBLIC;

    override fun toString() = when (this) {
        PACKAGE -> ""
        else -> this.name.lowercase()
    }
}

sealed class AccessModifier {
    abstract val name: String

    abstract fun canAccess(other: AccessModifier): Boolean

    object Public : AccessModifier() {
        override val name: String = "public"

        override fun canAccess(other: AccessModifier): Boolean = other is Public
    }

    object Private : AccessModifier() {
        override val name: String = "private"

        override fun canAccess(other: AccessModifier): Boolean = true
    }

    data class Protected(val klass: KfgClass) : AccessModifier() {
        override val name: String = "protected"

        override fun canAccess(other: AccessModifier): Boolean = when (other) {
            is Public -> true
            is Protected -> other.klass.isInheritorOf(this.klass)
            is Package -> other.pkg == this.klass.pkg
            is Private -> false
        }
    }

    data class Package(val pkg: KfgPackage) : AccessModifier() {
        override val name: String = "package"

        override fun canAccess(other: AccessModifier): Boolean = when (other) {
            is Public -> true
            is Package -> other.pkg == this.pkg
            is Protected -> other.klass.pkg == this.pkg
            is Private -> false
        }
    }

    override fun toString(): String = name
}

val KfgClass.accessModifier: AccessModifier
    get() = when {
        this.isPrivate -> AccessModifier.Private
        this.isProtected -> AccessModifier.Protected(this.superClass!!)
        this.isPublic -> AccessModifier.Public
        else -> AccessModifier.Package(this.pkg)
    }

val KfgMethod.accessModifier: AccessModifier
    get() = when {
        this.isPrivate -> AccessModifier.Private
        this.isProtected -> AccessModifier.Protected(this.klass)
        this.isPublic -> AccessModifier.Public
        else -> AccessModifier.Package(this.klass.pkg)
    }

val KfgField.accessModifier: AccessModifier
    get() = when {
        this.isPrivate -> AccessModifier.Private
        this.isProtected -> AccessModifier.Protected(this.klass)
        this.isPublic -> AccessModifier.Public
        else -> AccessModifier.Package(this.klass.pkg)
    }

@Suppress("RecursivePropertyAccessor")
val KfgType.accessModifier: AccessModifier
    get() = when (this) {
        is ClassType -> this.klass.accessModifier
        is ArrayType -> this.component.accessModifier
        else -> AccessModifier.Public
    }

fun KexType.getAccessModifier(tf: TypeFactory) = this.getKfgType(tf).accessModifier
fun Class<*>.getAccessModifier(cm: ClassManager): AccessModifier =
    this.superclass.toKfgType(cm.type).accessModifier

fun Method.getAccessModifier(cm: ClassManager): AccessModifier = when {
    this.isPrivate -> AccessModifier.Private
    this.isPublic -> AccessModifier.Public
    this.isProtected -> (this.declaringClass.toKfgType(cm.type) as ClassType).klass.let {
        AccessModifier.Protected(it)
    }
    else -> (this.declaringClass.toKfgType(cm.type) as ClassType).klass.let {
        AccessModifier.Package(it.pkg)
    }
}

fun Field.getAccessModifier(cm: ClassManager): AccessModifier = when {
    this.isPrivate -> AccessModifier.Private
    this.isPublic -> AccessModifier.Public
    this.isProtected -> (this.declaringClass.toKfgType(cm.type) as ClassType).klass.let {
        AccessModifier.Protected(it)
    }
    else -> (this.declaringClass.toKfgType(cm.type) as ClassType).klass.let {
        AccessModifier.Package(it.pkg)
    }
}
