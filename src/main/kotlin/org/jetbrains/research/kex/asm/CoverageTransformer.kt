package org.jetbrains.research.kex.asm

import jdk.internal.org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.*


class CoverageTransformer(ct: ClassTransformer?) : ClassTransformer(ct) {

    override fun transform(cn: ClassNode) {
        for (mn in cn.methods as List<MethodNode>) {
            val insns = mn.instructions
            if (insns.size() == 0) {
                continue
            }
            val j = insns.iterator()
            while (j.hasNext()) {
                val `in` = j.next()
                val op = `in`.getOpcode()
                if (op >= Opcodes.IFEQ && op <= Opcodes.IF_ACMPNE) {
                    val il = InsnList()
                    il.add(FieldInsnNode(Opcodes.GETSTATIC, "java/lang/System", "err", "Ljava/io/PrintStream;"))
                    il.add(LdcInsnNode("JUMPING"))
                    il.add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false))
                    insns.insert(`in`.previous, il)
                }
            }
        }
        super.transform(cn)
    }
}