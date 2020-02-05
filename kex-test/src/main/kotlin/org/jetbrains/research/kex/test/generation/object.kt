package org.jetbrains.research.kex.test.generation

import org.jetbrains.research.kex.test.Intrinsics


class ObjectGenerationTests {
    class Point(val x: Int, val y: Int) {
        override fun toString(): String {
            return "($x, $y)"
        }

    }

    class Line(var start: Point?, var end: Point?) {
        override fun toString(): String {
            return "{$start, $end}"
        }

    }

    fun testLine(line: Line?) {
        if (line == null) return
        if (line.start != null) {
            Intrinsics.assertReachable()
        }
        if (line.end != null) {
            Intrinsics.assertReachable()
        }
        if (line.start == line.end) {
            Intrinsics.assertReachable()
        }
        Intrinsics.assertReachable()
    }
}