package org.jetbrains.research.kex.asm.analysis

import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.util.GraphTraversal

interface SearchStrategy : Iterable<BasicBlock> {
    val method: Method
}

class TopologicalStrategy(override val method: Method) : SearchStrategy {
    val order = GraphTraversal(method).topologicalSort().reversed()

    override fun iterator() = order.iterator()
}

class BfsStrategy(override val method: Method) : SearchStrategy {
    val bfsSearch = GraphTraversal(method).bfs()

    override fun iterator() = bfsSearch.iterator()
}

class DfsStrategy(override val method: Method) : SearchStrategy {
    val dfsSearch = GraphTraversal(method).dfs()

    override fun iterator() = dfsSearch.iterator()
}
