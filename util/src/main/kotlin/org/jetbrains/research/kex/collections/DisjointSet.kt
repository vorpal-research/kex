package org.jetbrains.research.kex.collections

class Subset<T : Any?>(val data: T) {
    var parent = this
        internal set
    var rank = 0
        internal set

    fun isRoot() = parent == this

    fun getRoot(): Subset<T> = if (!isRoot()) {
        val ancestor = parent.getRoot()
        parent = ancestor
        ancestor
    } else this

    override fun hashCode() = 0
    override fun equals(other: Any?) = this === other
}

class DisjointSet<T : Any?>(private val children: MutableSet<Subset<T>> = mutableSetOf()) : MutableSet<Subset<T>> by children {
    fun find(element: Subset<T>) = element.getRoot()
    fun findUnsafe(element: Subset<T>?) = element?.getRoot()

    fun join(lhv: Subset<T>, rhv: Subset<T>): Subset<T> {
        val lhvRoot = lhv.getRoot()
        val rhvRoot = rhv.getRoot()

        return when {
            lhvRoot == rhvRoot -> lhvRoot
            lhvRoot.rank < rhvRoot.rank -> {
                lhvRoot.parent = rhvRoot
                rhvRoot
            }
            lhvRoot.rank > rhvRoot.rank -> {
                rhvRoot.parent = lhvRoot
                lhvRoot
            }
            else -> {
                rhvRoot.parent = lhvRoot
                ++rhvRoot.rank
                lhvRoot
            }
        }
    }

    fun emplace(element: T): Subset<T> {
        val wrapped = Subset(element)
        add(wrapped)
        return wrapped
    }
}