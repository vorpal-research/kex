package org.vorpal.research.kex.launcher

import org.vorpal.research.kex.asm.util.AccessModifier
import org.vorpal.research.kex.asm.util.Visibility
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.util.asmString
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.Package
import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kfg.ir.ConcreteClass
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.type.parseStringToType

sealed class AnalysisLevel {
    abstract val pkg: Package
    abstract val levelName: String

    companion object {
        fun parse(cm: ClassManager, targetName: String): AnalysisLevel {
            val packageRegex = """[\w$]+(\.[\w$]+)*\.\*"""
            val klassNameRegex = """(${packageRegex.dropLast(2)})?[\w$]+"""
            val methodNameRegex = """(<init>|<clinit>|(\w+))"""
            val typeRegex = """(void|((byte|char|short|int|long|float|double|$klassNameRegex)(\[\])*))"""
            return when {
                targetName == "${Package.EXPANSION}" -> PackageLevel(Package.defaultPackage)
                targetName.matches(Regex(packageRegex)) -> PackageLevel(Package.parse(targetName))
                targetName.matches(Regex("""$klassNameRegex::$methodNameRegex\((($typeRegex,\s*)*$typeRegex)?\):\s*$typeRegex""")) -> {
                    val (klassName, methodFullDesc) = targetName.split("::")
                    val (methodName, methodArgs, methodReturn) = methodFullDesc.split("(", "):")
                    val klass = cm[klassName.asmString]
                    if (klass !is ConcreteClass) {
                        throw LauncherException("Target class $klassName is not found in the classPath")
                    }
                    val method = klass.getMethod(
                        methodName,
                        parseStringToType(
                            cm.type,
                            methodReturn.trim().asmString
                        ),
                        *methodArgs.trim().split(""",\s*""".toRegex()).filter { it.isNotBlank() }.map {
                            parseStringToType(cm.type, it.asmString)
                        }.toTypedArray()
                    )
                    MethodLevel(method)
                }

                targetName.matches(Regex(klassNameRegex)) -> {
                    val klass = cm[targetName.asmString]
                    if (klass !is ConcreteClass) {
                        throw LauncherException("Target class $targetName is not found in the classPath")
                    }
                    ClassLevel(klass)
                }

                else -> throw LauncherException("Could not parse target $targetName")
            }
        }
    }

    val accessLevel: AccessModifier
        get() = when (kexConfig.getEnumValue("testGen", "accessLevel", true, Visibility.PUBLIC)) {
            Visibility.PRIVATE -> AccessModifier.Private
            Visibility.PROTECTED -> AccessModifier.Protected(
                (this as? ClassLevel)?.klass
                    ?: throw LauncherException("For 'protected' access level the target should be a class")
            )

            Visibility.PACKAGE -> AccessModifier.Package(this.pkg.concretePackage)
            Visibility.PUBLIC -> AccessModifier.Public
        }
}

data class PackageLevel(override val pkg: Package) : AnalysisLevel() {
    override val levelName: String
        get() = "package"

    override fun toString() = "package $pkg"
}

data class ClassLevel(val klass: Class) : AnalysisLevel() {
    override val levelName: String
        get() = "class"
    override val pkg = klass.pkg
    override fun toString() = "class $klass"
}

data class MethodLevel(val method: Method) : AnalysisLevel() {
    override val levelName: String
        get() = "method"
    override val pkg = method.klass.pkg
    override fun toString() = "method $method"
}
