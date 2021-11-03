package org.jetbrains.research.kex.asm.analysis.libchecker

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.MethodDesc
import org.jetbrains.research.libsl.asg.Function

fun Function.desc(cm: ClassManager): MethodDesc {
        val args = args.map { it.type.kfgType(cm) }.toTypedArray()
        val returnType = returnType?.kfgType(cm) ?: cm.type.voidType
        return MethodDesc(args, returnType)
    }