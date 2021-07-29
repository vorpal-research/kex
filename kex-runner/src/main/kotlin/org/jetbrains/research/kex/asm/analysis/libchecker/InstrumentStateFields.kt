package org.jetbrains.research.kex.asm.analysis.libchecker

import org.jetbrains.research.kex.libsl.LibslDescriptor
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Field
import org.jetbrains.research.kfg.type.IntType
import org.jetbrains.research.kfg.visitor.ClassVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.FieldNode

class InstrumentStateFields(
    override val cm: ClassManager,
    private val librarySpecification: LibslDescriptor
    ) : ClassVisitor {
    val stateFields = mutableMapOf<Class, Field>()

    private val prefix = "\$KEX\$INSTRUMENTED\$"

    override fun visit(klass: Class) {
        val fullyName = klass.fullName.replace("/", ".")
        val classSpec = librarySpecification.automatonByQualifiedName[fullyName] ?: return
        if (classSpec.states.isNotEmpty()) {
            if (klass !in stateFields.keys) {
                val stateFieldName = prefix + "STATE"

                val fn = FieldNode(Opcodes.ACC_PUBLIC, stateFieldName, "I", null, 0) // I = int
                val stateField = Field(cm, fn, klass)
                klass.cn.fields.add(fn)
                klass.modifyField(stateField, IntType)
                stateFields[klass] = stateField
            }
        }
    }

    override fun cleanup() { }
}