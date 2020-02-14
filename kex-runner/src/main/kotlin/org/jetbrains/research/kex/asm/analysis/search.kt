package org.jetbrains.research.kex.asm.analysis

import com.abdullin.kthelper.algorithm.GraphTraversal
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method

interface SearchStrategy : Iterable<BasicBlock> {
    val method: Method
}

class TopologicalStrategy(override val method: Method) : SearchStrategy {
    val order = GraphTraversal(method).topologicalSort()

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
