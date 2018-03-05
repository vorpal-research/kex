package org.jetbrains.research.kex.asm

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.*


class CoverageTransformer : MethodTransformer {
    private enum class cmpType {ZERO_CMP, BIN_ICMP, BIN_ACMP}

    constructor() : this(null)
    constructor(mt: MethodTransformer?) : super(mt)

    private fun printLocalInt(il: InsnList, local: Int) {
        il.add(FieldInsnNode(Opcodes.GETSTATIC, "java/lang/System", "err", "Ljava/io/PrintStream;"))
        il.add(VarInsnNode(Opcodes.ILOAD, local))
        il.add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(I)V", false))
    }

    private fun printLocalObject(il: InsnList, local: Int) {
        il.add(FieldInsnNode(Opcodes.GETSTATIC, "java/lang/System", "err", "Ljava/io/PrintStream;"))
        il.add(VarInsnNode(Opcodes.ALOAD, local))
        il.add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/Object;)V", false))
    }

    private fun printString(il: InsnList, str: String) {
        il.add(FieldInsnNode(Opcodes.GETSTATIC, "java/lang/System", "err", "Ljava/io/PrintStream;"))
        il.add(LdcInsnNode(str))
        il.add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false))
    }

    override fun transform(mn: MethodNode) {
        val insns = mn.instructions
        if (insns.size() == 0) return

        var localIndx = mn.maxLocals
        val it = insns.iterator() as MutableListIterator<AbstractInsnNode>
        while (it.hasNext()) {
            val inst = it.next()
            val op = inst.getOpcode()
            if (Opcodes.IFEQ <= op && op <= Opcodes.IF_ACMPNE) {
                val storeOpcode = when {
                    Opcodes.IFEQ <= op && op <= Opcodes.IFLE -> cmpType.ZERO_CMP
                    Opcodes.IF_ICMPEQ <= op && op <= Opcodes.IF_ICMPLE -> cmpType.BIN_ICMP
                    else -> cmpType.BIN_ACMP
                }
                val il = InsnList()
                when (storeOpcode) {
                    cmpType.ZERO_CMP -> {
                        val newLocal = localIndx++
                        il.add(VarInsnNode(Opcodes.ISTORE, newLocal))
                        printLocalInt(il, newLocal)
                        il.add(VarInsnNode(Opcodes.ILOAD, newLocal))
                    }
                    cmpType.BIN_ICMP -> {
                        val newLocal1 = localIndx++
                        val newLocal2 = localIndx++
                        il.add(VarInsnNode(Opcodes.ISTORE, newLocal1))
                        il.add(VarInsnNode(Opcodes.ISTORE, newLocal2))
                        printLocalInt(il, newLocal2)
                        printLocalInt(il, newLocal1)
                        il.add(VarInsnNode(Opcodes.ILOAD, newLocal2))
                        il.add(VarInsnNode(Opcodes.ILOAD, newLocal1))
                    }
                    cmpType.BIN_ACMP -> {
                        val newLocal1 = localIndx++
                        val newLocal2 = localIndx++
                        il.add(VarInsnNode(Opcodes.ASTORE, newLocal1))
                        il.add(VarInsnNode(Opcodes.ASTORE, newLocal2))
                        printLocalObject(il, newLocal2)
                        printLocalObject(il, newLocal1)
                        il.add(VarInsnNode(Opcodes.ALOAD, newLocal2))
                        il.add(VarInsnNode(Opcodes.ALOAD, newLocal1))
                    }
                }
                printString(il, "JUMPING with opcode $op")
                insns.insert(inst.previous, il)
            }
        }
        mn.maxStack += 2
        mn.maxLocals = localIndx
        super.transform(mn)
    }
}