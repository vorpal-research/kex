package org.jetbrains.research.kex.test.generation

import org.jetbrains.research.kex.Intrinsics.kexAssert


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
            kexAssert()
        }
        if (line.end != null) {
            kexAssert()
        }
        if (line.start == line.end) {
            kexAssert()
        }
        kexAssert()
    }
}