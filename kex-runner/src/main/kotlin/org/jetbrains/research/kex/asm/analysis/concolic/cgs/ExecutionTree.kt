package org.jetbrains.research.kex.asm.analysis.concolic.cgs

import org.jetbrains.research.kex.state.BasicState
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.predicate.PredicateType
import org.jetbrains.research.kex.trace.symbolic.Clause
import org.jetbrains.research.kex.trace.symbolic.SymbolicState
import org.jetbrains.research.kfg.ir.value.instruction.Instruction
import org.jetbrains.research.kthelper.algorithm.GraphView
import org.jetbrains.research.kthelper.algorithm.Viewable
import org.jetbrains.research.kthelper.assert.ktassert
import org.jetbrains.research.kthelper.logging.log
import java.util.*

data class Statement(
    val instruction: Instruction,
    val predicate: Predicate
)

sealed class Node(
    val statements: List<Statement>,
    val upEdge: Node?
) {
    val downEdges = mutableMapOf<Clause, Node>()

    operator fun contains(clause: Clause) = clause in downEdges
    operator fun get(clause: Clause) = downEdges.getValue(clause)
    operator fun set(clause: Clause, node: Node) {
        downEdges[clause] = node
    }
}

class RootNode(statements: List<Statement>) : Node(statements.toList(), null)
class TreeNode(statements: List<Statement>, upEdge: Node) : Node(statements.toList(), upEdge)

class ExecutionTree : Viewable {
    private var root: Node? = null

    fun addTrace(symbolicState: SymbolicState) {
        var old: Node? = root
        var entryClause: Clause? = null
        var statements = mutableListOf<Statement>()

        for (predicate in (symbolicState.state as BasicState)) {
            when (predicate.type) {
                is PredicateType.Path -> {
                    val clause = Clause(symbolicState[predicate], predicate)
                    old = when {
                        old == null -> RootNode(statements).also {
                            root = it
                        }
                        entryClause == null -> old.also { ktassert(it.statements == statements) { log.error("Traces contradict") } }
                        entryClause in old -> old[entryClause]
                        else -> TreeNode(statements, old).also {
                            old!![entryClause!!] = it
                        }
                    }
                    entryClause = clause
                    statements = mutableListOf()
                }
                else -> statements.add(Statement(symbolicState[predicate], predicate))
            }
        }
        when {
            old == null -> root = RootNode(statements)
            entryClause == null -> {}
            entryClause in old -> {}
            else -> old[entryClause] = TreeNode(statements, old)
        }


//        if (statements.isNotEmpty()) {
//            when {
//                old == null -> root = RootNode(statements)
//                entryClause == null -> {}
//                entryClause in old -> {}
//                else -> old[entryClause] = TreeNode(statements, old)
//            }
//        } else if (entryClause != null) {
//            when {
//                old == null -> root = RootNode(statements)
//                entryClause in old -> {}
//                else -> old[entryClause] = TreeNode(statements, old)
//            }
//        }
    }

    private fun collectTree(): List<Node> = mutableListOf<Node>().apply {
        collectTree(root, this)
    }

    private fun collectTree(node: Node?, mutableList: MutableList<Node>) {
        if (node == null) return
        mutableList.add(node)
        for ((_, child) in node.downEdges) {
            collectTree(child, mutableList)
        }
    }

    override val graphView: List<GraphView>
        get() {
            val treeNodes = collectTree()
            val nodes = IdentityHashMap<Node, GraphView>()

            for (node in treeNodes) {
                val label = node.statements.joinToString("\n") { it.predicate.print() }
                nodes[node] = GraphView(System.identityHashCode(node).toString(), label)
            }

            for (node in treeNodes) {
                val current = nodes.getValue(node)
                for ((clause, child) in node.downEdges) {
                    current.addSuccessor(nodes.getValue(child), clause.predicate.print())
                }
            }

            return nodes.values.toList()
        }
}