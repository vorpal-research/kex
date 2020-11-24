@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug

class BasicTests {

    enum class Planet(val order: Int) {
        MERCURY(1),
        VENICE(2),
        EARTH(3)
    }

    fun test(planet: Planet) {
        if (planet.order == 2) {
            println("it's venice")
        }
    }

//    interface A {
//        fun a(): Int
//    }
//
//    class B : A {
//        override fun a(): Int = 2
//    }
//
//    class C : A {
//        override fun a(): Int = -2
//    }
//
//    class Cont(val a: A) {
//        val d = a.a()
//    }
//
//    fun test(c: Cont) {
//        if (c.a is C) {
//            println("a")
//        }
//    }

//    class Point(val x: Int, val y: Int)
//
//    fun test(a: ArrayList<Point>) {
//        if (a.size == 2) {
//            if (a[0].x == 10) {
//                if (a[1].y == 11) {
//                    error("a")
//                }
//            }
//        }
//    }
}