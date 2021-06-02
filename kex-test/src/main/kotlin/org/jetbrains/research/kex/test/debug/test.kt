@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug

class BasicTests {
    fun entry(a: Int, b: Int, c: Int): Int {
        val int = a + b + c
        val res = try {
            throwNpe(a + 1, b + 1, c + 1)
        } catch (e: IllegalArgumentException) {
            int
        }
        return res
    }

    fun throwNpe(a: Int, b: Int, c: Int): Int = throwIse(a + 2, b + 2, c + 2)

    fun throwIse(a: Int, b: Int, c: Int): Int {
        return throwIae(a + 3, b + 3, c + 3)
    }

    fun throwIae(a: Int, b: Int, c: Int): Int {
        throw IllegalArgumentException()
    }
}