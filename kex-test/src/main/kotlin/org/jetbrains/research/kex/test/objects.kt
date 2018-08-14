package org.jetbrains.research.kex.test

class Point(val x: Int = 0, val y: Int = 0)

class ObjectTests {
    fun simplePointCheck() {
        val zero = Point()
        val ten = Point(x = 10, y = 10)

        if (ten.x > zero.x) {
            Intrinsics.assertReachable()
        } else {
            // can't handle getters and setters yet
            Intrinsics.assertReachable()
        }
    }
}