package org.jetbrains.research.kex.asm.manager

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.MethodDesc
import org.jetbrains.research.kfg.type.*
import org.jetbrains.research.kfg.visitor.MethodVisitor
import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log

class OriginalMapper(
    override val cm: ClassManager,
    private val originalCM: ClassManager,
) : MethodVisitor {
    private lateinit var currentMethod: Method
    private lateinit var originalMethod: Method

    override fun cleanup() {}

    override fun visit(method: Method) {
        currentMethod = method
        originalMethod = method.originalMethod
        method.original = originalMethod
        super.visit(method)
    }

    override fun visitBasicBlock(bb: BasicBlock) {
        bb.original = bb.originalBlock
    }

    private val Class.original: Class get() = originalCM[fullName]
    private val MethodDesc.original: MethodDesc
        get() = MethodDesc(
            args.map { it.original }.toTypedArray(),
            returnType.original
        )
    private val Method.originalMethod: Method get() = this.klass.original.getMethod(this.name, this.desc.original)
    private val BasicBlock.originalBlock: BasicBlock get() = originalMethod.getBlockByName(this.name.toString())!!

    private val Type.original: Type
        get() = when (this) {
            is BoolType -> originalCM.type.boolType
            is ByteType -> originalCM.type.byteType
            is CharType -> originalCM.type.charType
            is DoubleType -> originalCM.type.doubleType
            is FloatType -> originalCM.type.floatType
            is IntType -> originalCM.type.intType
            is LongType -> originalCM.type.longType
            is NullType -> originalCM.type.nullType
            is ShortType -> originalCM.type.shortType
            is VoidType -> originalCM.type.voidType
            is ClassType -> originalCM.type.getRefType(this.klass.original)
            is ArrayType -> originalCM.type.getArrayType(this.component.original)
            else -> unreachable { log.error("Unknown type $this") }
        }
}