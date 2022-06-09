package org.vorpal.research.kex.asm.manager

import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.ir.Field
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.ThisRef
import org.vorpal.research.kfg.ir.value.instruction.FieldStoreInst
import org.vorpal.research.kfg.visitor.MethodVisitor
import org.vorpal.research.kthelper.logging.log

@Deprecated(message = "outdated")
class AccessInfoCollector(override val cm: ClassManager) : MethodVisitor {
    private val fieldAccessSet = mutableSetOf<Field>()

    companion object {
        val methodAccessMap = hashMapOf<Method, Set<Field>>()
    }

    override fun cleanup() {
        fieldAccessSet.clear()
    }

    override fun visit(method: Method) {
        super.visit(method)
        methodAccessMap[method] = fieldAccessSet.toSet()
        if (fieldAccessSet.isNotEmpty())
            log.info("Collected method $method access info: ${fieldAccessSet.joinToString(", ")}")
    }

    override fun visitFieldStoreInst(inst: FieldStoreInst) {
        if (inst.isStatic || inst.owner is ThisRef) {
            fieldAccessSet += inst.field
        }
        super.visitFieldStoreInst(inst)
    }
}
