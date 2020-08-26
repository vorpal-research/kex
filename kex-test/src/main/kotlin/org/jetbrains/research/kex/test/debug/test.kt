@file:Suppress("SENSELESS_COMPARISON", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package org.jetbrains.research.kex.test.debug

class BasicTests {

    class Value(val name: String)

    class LocalArray(private val locals: MutableMap<Int, Value> = hashMapOf()) : MutableMap<Int, Value> by locals {
    override fun clear() {
        locals.clear()
    }

    override fun put(key: Int, value: Value): Value? {
        val prev = locals.put(key, value)
        return prev
    }

    override fun putAll(from: Map<out Int, Value>) {
        from.forEach {
            put(it.key, it.value)
        }
    }

    override fun remove(key: Int): Value? {
        val res = locals.remove(key)
        return res
    }
}

    fun testAbstractClass(array: LocalArray) {
        println("a")
    }

}