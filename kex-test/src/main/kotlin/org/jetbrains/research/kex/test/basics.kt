@file:Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")
package org.jetbrains.research.kex.test

import org.jetbrains.research.kex.Intrinsics.kexAssert
import org.jetbrains.research.kex.Intrinsics.kexUnreachable
import kotlin.math.abs

class BasicTests {
    fun testPlain(a: Int, b: Int): Int {
        val i = 10
        val j = 152
        val k = j + i - 15

        val max = maxOf(a, b, k)
        kexAssert()
        return max
    }

    fun testIf(a: Int, b: Int): Int {
        val res = if (a > b) {
            kexAssert(a > b)
            a - b
        } else {
            kexAssert(a <= b)
            a + b
        }

        kexAssert()
        println(res)
        return res
    }

    fun testWhen(a: Char): Int {
        val y = when (a) {
            'a' -> 5
            'b' -> 4
            'c' -> 3
            'd' -> 2
            'f' -> 1
            else -> {
                println("You suck")
                -1
            }
        }
        if (y == 10) {
            kexUnreachable()
        }
        if (y in 1..5) {
            kexAssert()
        }
        return y
    }

    fun testLoop(a: Int, b: Int): Int {
        var x = a - b
        while (x < a) {
            kexAssert(x < a)
            if (b > x) {
                kexAssert(b > x, x < a)
                println("b bigger")
            }
            ++x
        }
        kexAssert()
        return x
    }

    fun testUnreachableIf(x: Int): Int {
        val set = "asdasdal;djadslas;d".length
        val z = 10
        val y = if (x > z && x < 0) {
            kexUnreachable()
            println("lol")
            142
        } else {
            kexAssert(x <= z || x >= 0)
            println("lol2")
            x- 2 * x
        }
        kexAssert()
        return y
    }

    fun testUnreachableLoop(): Int {
        var x = 10
        while (x > 5) {
            kexAssert(x > 5)
            ++x
        }
        kexUnreachable()
        println(x)
        return x
    }

    fun testArray(): Int {
        val array = arrayOf(
                arrayOf(0, 1, 2, 3, 4),
                arrayOf(5, 6, 7, 8, 9),
                arrayOf(10, 11, 12, 13, 14)
        )
        if (array[2][4] > 10) {
            kexAssert()
        }
        if (array.size > 2) {
            kexAssert()
        }
        if (array[0].size > 4) {
            kexAssert()
        }
        return array.flatten().reduce { a, b -> a + b}
    }

    fun testUnreachableArray(): Int {
        val array = arrayOf(
                arrayOf(0, 1, 2, 3, 4),
                arrayOf(5, 6, 7, 8, 9),
                arrayOf(10, 11, 12, 13, 14)
        )
        if (array[4][4] > 10) {
            kexUnreachable()
        }
        return array.flatten().reduce { a, b -> a + b}
    }

    fun testSimpleOuterCall(): Int {
        val a = 10
        val b = 42
        val c = abs(a - b)
        // we don't know anything about function `abs`, so result is unknown
        if (c < 0) kexAssert()
        else kexAssert()
        return c
    }

    fun triangleKind(a: Double, b: Double, c: Double): Double {
        val max = maxOf(a, b, c)
        if (2 * max > a + b + c)
            return -1.0
        val res = 2 * max * max - a * a - b * b - c * c
        return res
    }

    fun digitNumber(n: Int): Int =
            if (n in -9..9) 1
            else digitNumber(n / 10) + digitNumber(n % 10)
}