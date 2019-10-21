@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug

import kotlin.math.abs

fun lol(n: Int): Int {
    if (n > 10) println(10)
    println("aaa")
    if (n > 100) println(1000)
    return n * n
}


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
    return y
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

fun testUnreachableIf(x: Int): Int {
    val set = "asdasdal;djadslas;d".length
    val z = 10
    val y = if (x > z && x < 0) {
        println("lol")
        142
    } else {
        println("lol2")
        x- 2 * x
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

fun testArray(): Int {
    val array = arrayOf(
            arrayOf(0, 1, 2, 3, 4),
            arrayOf(5, 6, 7, 8, 9),
            arrayOf(10, 11, 12, 13, 14)
    )
    return array.flatten().reduce { a, b -> a + b}
}

fun testUnreachableArray(): Int {
    val array = arrayOf(
            arrayOf(0, 1, 2, 3, 4),
            arrayOf(5, 6, 7, 8, 9),
            arrayOf(10, 11, 12, 13, 14)
    )
    return array.flatten().reduce { a, b -> a + b}
}

fun testSimpleOuterCall(): Int {
    val a = 10
    val b = 42
    val c = abs(a - b)
    return c
}

fun triangleKind(a: Double, b: Double, c: Double): Double {
    val max = maxOf(a, b, c)
    if (2 * max > a + b + c)
        return -1.0
    val res = 2 * max * max - a * a - b * b - c * c
    return res
}

class A {
    fun main(args: Array<String>) {
        if (args[0] == "a") lol(10)
        if (args[1].length > 2) testPlain(0, 1)
        if (args[2] < args[3]) testArray()
        if (args[4] == "b") triangleKind(1.0, 2.0, 4.0)
        if (args[5].length < 5) testSimpleOuterCall()
    }
}