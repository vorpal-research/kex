package org.jetbrains.research.kex.test

class BasicTests {
    fun testPlain(a: Int, b: Int): Int {
        val i = 10
        val j = 152
        val k = j + i - 15

        val max = maxOf(a, b, k)
        return max
    }

    fun testIf(a: Int, b: Int): Int {
        val res = if (a > b) {
            a - b
        } else {
            a + b
        }

        println(res)
        return res
    }

    fun testLoop(a: Int, b: Int): Int {
        var x = a - b
        while (x < a) {
            if (b > x) {
                println("b bigger")
            }
            ++x
        }
        return x
    }

    fun testUnreachableIf(): Int {
        val y = if (10 > 15) {
            142
        } else {
            -15
        }
        return y
    }

    fun testUnreachableLoop(): Int {
        var x = 10
        while (x > 5) {
            ++x
        }
        println(x)
        return x
    }
}