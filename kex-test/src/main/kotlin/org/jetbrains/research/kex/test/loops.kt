package org.jetbrains.research.kex.test

class LoopTests {
    fun simpleLoop() {
        var i = 0
        val loop = arrayOf(1, 2, 123, 123, 123, 324)
        if (loop.size > 0) println("has elements")
        else println("empty")
        while (i < 5) {
            println(loop[i])
            ++i
        }
    }

    fun simpleLoop(size: Int) {
        var i = 0
        val loop = arrayOf(1, 2, 123, 123, 123, 324)
        if (loop.size > 0) println("has elements")
        else println("empty")
        if (loop.size >= size) {
            println("Index out of bounds")
            return
        }
        while (i < size) {
            println(loop[i])
            ++i
        }
    }

    fun simpleInnerLoop() {
        val loop = arrayOf(1, 2, 3, 4, 5, 6, 7)
        for (i in 0..5) {
            for (j in i..6) {
                for (k in i..6) {
                    if (loop[k] < loop[i]) {
                        val temp = loop[i]
                        loop[i] = loop[k]
                        loop[j] = temp
                    }
                }
            }
        }
    }

    fun loopBreak() {
        val loop = arrayOf(1, 2, 123, 123, 123, 324)
        for (it in loop) {
            println(it)
            if (it == 10) break
        }
    }

    fun loopContinue() {
        val loop = arrayOf(1, 2, 123, 123, 123, 324)
        var i = 0
        while (i < 5) {
            if (loop[i] == 10) continue
            println(loop[i])
            ++i
        }
    }

    fun loopAll() {
        val loop = arrayOf(1, 2, 123, 123, 123, 324)
        var i = 0
        while (i < loop.size) {
            if (loop[i] == 10) continue
            println(loop[i])

            val loop2 = arrayOf(1, 2, 123, 123, 123, 324)
            var i2 = 0
            while (i2 < i) {
                if (loop2[i2] == 10) continue
                println(loop2[i2])

                val loop3 = arrayOf(1, 2, 123, 123, 123, 324)
                for (it in loop3) {
                    println(it)
                    if (it == 10) break
                }
                ++i2
            }
            ++i


            var i3 = 0
            while (i3 < loop.size) {
                if (loop[i3] == 10) continue
                println(loop[i])
                ++i3
            }
            ++i
        }
    }

    fun loopTryCatch() {
        val names = mutableListOf("a", "b", "c")
        for (name in names) {
            try {
                val concater = StringBuilder()
//                for (i in 0..name.length) concater.append(name[i] + 1)
            } catch (e: IndexOutOfBoundsException) {
                println("Oops")
            }
        }
    }
}