package org.jetbrains.research.kex.asm.manager

import org.jetbrains.research.kthelper.logging.log
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.Field
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.ThisRef
import org.jetbrains.research.kfg.ir.value.instruction.FieldStoreInst
import org.jetbrains.research.kfg.visitor.MethodVisitor

val Method.fieldAccessSet get() = AccessInfoCollector.methodAccessMap.getOrDefault(this, setOf())

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
