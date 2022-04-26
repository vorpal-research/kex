package org.jetbrains.research.kex.test.concolic.kaf

class Lesson6 {
    fun computeDeviceCells(cells: Int, commands: String, limit: Int): List<Int> {
        val cellsList = ArrayList<Int>(cells)
        if (commands == "") return cellsList
        var numberOfCommand = 0
        var numberOfCell: Int = cells / 2
        for (i in 1..limit) {
            when (commands[numberOfCommand]) {
                '+' -> cellsList[numberOfCell]++
                '-' -> cellsList[numberOfCell]--
                '>' -> numberOfCell++
                '<' -> numberOfCell--
                '[' -> if (cellsList[numberOfCell] == 0)
                    numberOfCommand += 5
                ']' -> if (cellsList[numberOfCell] != 0)
                    numberOfCommand -= 5
            }
            numberOfCommand++
            if (numberOfCell >= cells || numberOfCell < 0) throw IllegalStateException("The maximum value has been reached")
            if (numberOfCommand >= commands.length) break
        }
        return cellsList
    }
}