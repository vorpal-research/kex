package org.jetbrains.research.kex.asm.analysis

import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kthelper.algorithm.GraphTraversal

private fun buildBlockMapping(method: Method): Map<BasicBlock, BasicBlock> {
    val result = mutableMapOf<BasicBlock, BasicBlock>()
    val visited = mutableSetOf<BasicBlock>()

    for (block in method) {
        if (block in visited) continue

        val list = mutableListOf<BasicBlock>()
        var current = block
        while (true) {
            if (current.successors.size != 1) break
            val successor = current.successors.first()
            if (successor.predecessors.size != 1) break
            list += current
            current = successor
        }

        val last = list.lastOrNull() ?: continue
        for (b in list) {
            result[b] = last
            visited += b
        }
    }

    return result
}

private fun filterBlockOrder(method: Method, order: List<BasicBlock>): List<BasicBlock> {
    val mapping = buildBlockMapping(method)
    val mapped = order.map { mapping.getOrDefault(it, it) }
    val filteredMapping = mutableListOf<BasicBlock>()
    for (block in mapped) {
        if (block !in filteredMapping) filteredMapping += block
    }
    return filteredMapping
}

interface SearchStrategy : Iterable<BasicBlock> {
    val method: Method
}

class TopologicalStrategy(override val method: Method) : SearchStrategy {
    val order: List<BasicBlock>

    init {
        val tpOrder = GraphTraversal(method).topologicalSort()
        order = filterBlockOrder(method, tpOrder)
    }

    override fun iterator() = order.iterator()
}

class BfsStrategy(override val method: Method) : SearchStrategy {
    val bfsSearch: List<BasicBlock>

    init {
        val bfsOrder = GraphTraversal(method).bfs()
        bfsSearch = filterBlockOrder(method, bfsOrder)
    }

    override fun iterator() = bfsSearch.iterator()
}

class DfsStrategy(override val method: Method) : SearchStrategy {
    val dfsSearch: List<BasicBlock>

    init {
        val dfsOrder = GraphTraversal(method).dfs()
        dfsSearch = filterBlockOrder(method, dfsOrder)
    }

    override fun iterator() = dfsSearch.iterator()
}

class UnfilteredDfsStrategy(override val method: Method) : SearchStrategy {
    val dfsOrder: List<BasicBlock>

    init {
        dfsOrder = GraphTraversal(method).dfs()
    }

    override fun iterator() = dfsOrder.iterator()
}
