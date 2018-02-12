package org.jetbrains.research.kex.asm

import org.objectweb.asm.tree.ClassNode

open class ClassTransformer(var ct: ClassTransformer?) {

    open fun transform(cn: ClassNode) {
        if (ct != null) {
            ct!!.transform(cn)
        }
    }
}