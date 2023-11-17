package org.vorpal.research.kex.evolutions

import org.vorpal.research.kfg.ir.value.instruction.BinaryOpcode
import org.vorpal.research.kfg.ir.value.instruction.UnaryOpcode
import ru.spbstu.Apply
import ru.spbstu.Symbolic
import ru.spbstu.div
import ru.spbstu.gcd
import ru.spbstu.minus
import ru.spbstu.plus
import ru.spbstu.shl
import ru.spbstu.shr
import ru.spbstu.times
import ru.spbstu.unaryMinus

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

@Suppress("unused")
fun lcm(a: Long, b: Long): Long = (a / gcd(a, b)) * b
