package org.vorpal.research.kex.util

import org.vorpal.research.kfg.Package
import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kfg.type.ArrayType
import org.vorpal.research.kfg.type.ClassType
import org.vorpal.research.kfg.type.Type
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log

sealed class KfgTargetFilter {
    companion object {
        fun parse(string: String): KfgTargetFilter = when {
            string.startsWith("package ") -> KfgPackageTargetFilter(Package.parse(string.removePrefix("package ")))
            string.startsWith("class ") -> KfgClassTargetFilter(string.removePrefix("class ").asmString)
            else -> unreachable { log.error("Unknown target type for filter") }
        }
    }

    abstract fun matches(pkg: Package): Boolean
    abstract fun matches(klass: Class): Boolean

    abstract fun matches(klassName: String): Boolean

    open fun matches(type: Type): Boolean = when (type) {
        is ClassType -> matches(type.klass)
        is ArrayType -> matches(type.component)
        else -> false
    }
}

data class KfgPackageTargetFilter(val pkg: Package) : KfgTargetFilter() {
    override fun matches(pkg: Package): Boolean = this.pkg.isParent(pkg)

    override fun matches(klass: Class): Boolean = this.pkg.isParent(klass)

    override fun matches(klassName: String): Boolean = this.pkg.isParent(klassName)
}

data class KfgClassTargetFilter(val klassName: String) : KfgTargetFilter() {
    override fun matches(pkg: Package): Boolean = false

    override fun matches(klass: Class): Boolean = this.klassName == klass.fullName

    override fun matches(klassName: String): Boolean = this.klassName == klassName
}
