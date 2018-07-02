package org.jetbrains.research.kex.collections

fun <T: Any?> T?.wrap() = Mutable(this)

class Mutable<T : Any?> {
    var data: T?

    constructor(data: T?) {
        this.data = data
    }

    constructor() : this(null)

    fun valid() = data != null
    fun unwrap() = data!!

    override fun hashCode() = this.data?.hashCode() ?: 0
    override fun equals(other: Any?) = this.data == (other as? Mutable<*>)?.data

    infix fun `=`(other: T): T {
        this.data = other
        return other
    }

    infix fun `=`(other: Mutable<T>): Mutable<T> {
        this.data = other.data
        return other
    }
}