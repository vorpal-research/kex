package org.jetbrains.research.kex.trace.runtime

import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method


sealed class RuntimeAction

sealed class MethodAction(val method: Method) : RuntimeAction()
class MethodEntry(method: Method, val instance: Any?, val args: Array<Any?>) : MethodAction(method)
class MethodReturn(method: Method, val returnValue: Any?) : MethodAction(method)
class MethodThrow(method: Method, val throwable: Throwable) : MethodAction(method)

sealed class BlockAction(val block: BasicBlock) : RuntimeAction()
class BlockEntry(bb: BasicBlock) : BlockAction(bb)
class BlockJump(bb: BasicBlock) : BlockAction(bb)
class BlockBranch(bb: BasicBlock, val condition: Any?, val expected: Any?) : BlockAction(bb)
class BlockSwitch(bb: BasicBlock, val key: Any?) : BlockAction(bb)