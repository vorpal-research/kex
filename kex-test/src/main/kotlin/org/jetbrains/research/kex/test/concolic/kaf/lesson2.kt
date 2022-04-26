package org.jetbrains.research.kex.test.concolic.kaf

class Lesson2 {
    fun ageDescription(age: Int): Int {
        if (age < 0) return -1
        if (age > 200) return -1
        return when {
            age / 10 % 10 == 1 -> 0
            age % 10 == 1 -> 1
            age % 10 == 0 -> 0
            age % 10 <= 4 -> 2
            else -> 0
        }
    }

    fun whichRookThreatens(
        kingX: Int, kingY: Int,
        rookX1: Int, rookY1: Int,
        rookX2: Int, rookY2: Int
    ): Int {
        val threat1 = if (kingX == rookX1 || kingY == rookY1) 1 else 0
        val threat2 = if (kingX == rookX2 || kingY == rookY2) 2 else 0
        return threat1 + threat2
    }

    fun daysInMonth(month: Int, year: Int): Int {
        if (month < 1) return -1
        if (month > 12) return -1
        if (year < 0) return -1

        return when (month) {
            2 -> when {
                year % 400 == 0 -> 29
                year % 4 == 0 && year % 100 != 0 -> 29
                else -> 28
            }
            1, 3, 5, 7, 8, 10, 12 -> 31
            else -> 30
        }
    }
}