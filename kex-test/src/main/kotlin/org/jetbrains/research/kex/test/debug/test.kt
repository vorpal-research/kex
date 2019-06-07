@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE")

package org.jetbrains.research.kex.test.debug

class DoublePoint(val x: Double) {
    constructor() : this(0.0)

    fun lolol(d: Double): Double {
        return x + d
    }
}