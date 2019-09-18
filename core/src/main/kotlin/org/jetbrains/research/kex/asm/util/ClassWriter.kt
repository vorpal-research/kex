package org.jetbrains.research.kex.asm.util

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.util.writeClass
import org.jetbrains.research.kfg.visitor.ClassVisitor
import java.io.File

class ClassWriter(override val cm: ClassManager, val loader: ClassLoader, val target: File) : ClassVisitor {
    override fun cleanup() {}

    override fun visit(`class`: Class) {
        val classFileName = "${target.canonicalPath}/${`class`.fullname}.class"
        writeClass(cm, loader, `class`, classFileName)
    }
}
