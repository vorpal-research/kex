@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug

data class Point(val x: Int, val y: Int)

class P(val i: Int) {
    fun test(x: Int): Boolean {
        return i != x
    }
}

fun lol(a: Int): Int {
    println(a + a)
    return a * a
}

fun cycle(a: Int): Int {
    var x = 0
    var term = 0
    for (i in 1..3) {
        println(i)
        x = i
        term = lol(x)
        if (i > a) break
    }
    println(x)
    val p = P(term)
    if (p.test(x)) {
        println(term)
    }
    return x + term
}

fun moreTest() {
    println(testCallString("asdasdasd aaa"))
}

fun testCallString(string: String): Int {
    return string.length
}