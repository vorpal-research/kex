package org.jetbrains.research.kex.asm

import org.objectweb.asm.tree.MethodNode

open class MethodTransformer(val mt: MethodTransformer?) {
    open fun transform(mn: MethodNode) {
        if (mt != null) {
            mt.transform(mn)
        }
    }
}