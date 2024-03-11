@file:Suppress(
    "UNUSED_VARIABLE", "KotlinConstantConditions", "unused", "ConvertTwoComparisonsToRangeCheck",
    "MemberVisibilityCanBePrivate", "SpellCheckingInspection"
)

package org.vorpal.research.kex.test

import org.vorpal.research.kex.intrinsics.AssertIntrinsics.kexAssert
import org.vorpal.research.kex.intrinsics.AssertIntrinsics.kexUnreachable
import kotlin.math.abs

class BasicTests {
    fun testPlain(a: Int, b: Int): Int {
        val i = 10
        val j = 152
        val k = j + i - 15

        val max = maxOf(a, b, k)
        kexAssert(true)
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

        kexAssert(true)
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
            kexAssert(true)
        }
        return y
    }

    fun testLoop(a: Int, b: Int): Int {
        var x = a - b
        while (x < a) {
            kexAssert(x < a)
            if (b > x) {
                kexAssert(b > x)
                println("b bigger")
            }
            ++x
        }
        kexAssert(true)
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
            x - 2 * x
        }
        kexAssert(true)
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
            intArrayOf(0, 1, 2, 3, 4),
            intArrayOf(5, 6, 7, 8, 9),
            intArrayOf(10, 11, 12, 13, 14)
        )
        if (array[2][4] > 10) {
            kexAssert(true)
        }
        if (array.size > 2) {
            kexAssert(true)
        }
        if (array[0].size > 4) {
            kexAssert(true)
        }
        return array[0][0] + array[3][3]
    }

    fun testUnreachableArray(): Int {
        val array = arrayOf(
            intArrayOf(0, 1, 2, 3, 4),
            intArrayOf(5, 6, 7, 8, 9),
            intArrayOf(10, 11, 12, 13, 14)
        )
        if (array[4][4] > 10) {
            kexUnreachable()
        }
        return array[0][0] + array[3][3]
    }

    fun testSimpleOuterCall(): Int {
        val a = 10
        val b = 42
        val c = abs(a - b)
        if (c < 0) kexUnreachable()
        else kexAssert(true)
        return c
    }

    fun triangleKind(a: Double, b: Double, c: Double): Double {
        val max = maxOf(a, b, c)
        if (2 * max > a + b + c)
            return -1.0
        return 2 * max * max - a * a - b * b - c * c
    }

    fun digitNumber(n: Int): Int =
        if (n in -9..9) 1
        else digitNumber(n / 10) + digitNumber(n % 10)
}
