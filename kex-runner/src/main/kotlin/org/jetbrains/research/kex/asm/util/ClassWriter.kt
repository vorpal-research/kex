package org.jetbrains.research.kex.asm.util

import org.jetbrains.research.kex.ExecutionContext
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.util.write
import org.jetbrains.research.kfg.visitor.ClassVisitor
import org.jetbrains.research.kthelper.logging.log
import org.jetbrains.research.kthelper.tryOrNull
import java.nio.file.Path

class ClassWriter(val ctx: ExecutionContext, val target: Path) : ClassVisitor {
    override val cm: ClassManager
        get() = ctx.cm

    override fun cleanup() {}

    override fun visit(`class`: Class) {
        val classFileName = "${target.toAbsolutePath()}/${`class`.fullname}.class"
        tryOrNull {
            `class`.write(cm, ctx.loader, classFileName)
        } ?: log.warn("Could not write class $`class`")
    }
}
