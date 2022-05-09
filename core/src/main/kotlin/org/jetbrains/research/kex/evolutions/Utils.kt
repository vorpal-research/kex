package org.jetbrains.research.kex.evolutions

import org.jetbrains.research.kfg.ir.value.instruction.BinaryOpcode
import org.jetbrains.research.kfg.ir.value.instruction.UnaryOpcode
import ru.spbstu.*

fun BinaryOpcode.toFunc(): (Symbolic, Symbolic) -> Symbolic = when (this) {
    BinaryOpcode.ADD -> Symbolic::plus
    BinaryOpcode.SUB -> Symbolic::minus
    BinaryOpcode.MUL -> Symbolic::times
    BinaryOpcode.DIV -> Symbolic::div
    BinaryOpcode.SHL -> Symbolic::shl
    BinaryOpcode.SHR -> Symbolic::shr
    else -> { l, r -> KFGBinary(this, l, r) }
}

fun UnaryOpcode.toFunc(): (Symbolic) -> Symbolic = when (this) {
    UnaryOpcode.NEG -> Symbolic::unaryMinus
    UnaryOpcode.LENGTH -> { a -> Apply("\\length", a) }
}

fun lcm(a: Long, b: Long): Long = (a / gcd(a, b)) * b