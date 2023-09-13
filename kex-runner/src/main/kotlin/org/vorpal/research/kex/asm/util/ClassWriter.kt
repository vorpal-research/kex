package org.vorpal.research.kex.asm.util

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kfg.util.write
import org.vorpal.research.kfg.visitor.ClassVisitor
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.tryOrNull
import java.nio.file.Path
import java.nio.file.Paths

class ClassWriter(val ctx: ExecutionContext, val target: Path) : ClassVisitor {
    override val cm: ClassManager
        get() = ctx.cm

    override fun cleanup() {}

    override fun visit(klass: Class) {
        tryOrNull {
            val classFileName = target.resolve(Paths.get(klass.pkg.fileSystemPath, "${klass.name}.class")).toAbsolutePath()
            klass.write(cm, ctx.loader, classFileName)
        } ?: log.warn("Could not write class $klass")
    }
}
