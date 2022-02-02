package org.jetbrains.research.kex.asm.analysis.concolic.cgs

import org.jetbrains.research.kex.state.BasicState
import org.jetbrains.research.kex.state.predicate.Predicate
import org.jetbrains.research.kex.state.predicate.PredicateType
import org.jetbrains.research.kex.trace.symbolic.Clause
import org.jetbrains.research.kex.trace.symbolic.SymbolicState
import org.jetbrains.research.kfg.ir.value.instruction.Instruction
import org.jetbrains.research.kthelper.assert.ktassert
import org.jetbrains.research.kthelper.graph.GraphView
import org.jetbrains.research.kthelper.graph.Viewable
import org.jetbrains.research.kthelper.logging.log
import org.jetbrains.research.kthelper.tree.Tree
import java.util.*

data class Statement(
    val instruction: Instruction,
    val predicate: Predicate
)

sealed class Node(
    val statements: List<Statement>,
    override val parent: Node?
) : Tree.TreeNode<Node> {
    val downEdges = mutableMapOf<Clause, Node>()
    override val children: Set<Node>
        get() = downEdges.values.toSet()

    operator fun contains(clause: Clause) = clause in downEdges
    operator fun get(clause: Clause) = downEdges.getValue(clause)
    operator fun set(clause: Clause, node: Node) {
        downEdges[clause] = node
    }
}

class RootNode(statements: List<Statement>) : Node(statements.toList(), null)
class TreeNode(statements: List<Statement>, upEdge: Node) : Node(statements.toList(), upEdge)

class ExecutionTree : Tree<Node>, Viewable {
    private var innerRoot: Node? = null
    private val innerNodes = mutableSetOf<Node>()

    override val root: Node?
        get() = innerRoot
    override val nodes: Set<Node>
        get() = innerNodes

    fun addTrace(symbolicState: SymbolicState) {
        var old: Node? = innerRoot
        var entryClause: Clause? = null
        var statements = mutableListOf<Statement>()

        for (predicate in (symbolicState.state as BasicState)) {
            when (predicate.type) {
                is PredicateType.Path -> {
                    val clause = Clause(symbolicState[predicate], predicate)
                    old = when {
                        old == null -> RootNode(statements).also {
                            innerRoot = it
                            innerNodes += it
                        }
                        entryClause == null -> old.also { ktassert(it.statements == statements) { log.error("Traces contradict") } }
                        entryClause in old -> old[entryClause]
                        else -> TreeNode(statements, old).also {
                            old!![entryClause!!] = it
                            innerNodes += it
                        }
                    }
                    entryClause = clause
                    statements = mutableListOf()
                }
                else -> statements.add(Statement(symbolicState[predicate], predicate))
            }
        }
        when {
            old == null -> innerRoot = RootNode(statements).also {
                innerNodes += it
            }
            entryClause == null -> {}
            entryClause in old -> {}
            else -> old[entryClause] = TreeNode(statements, old).also {
                innerNodes += it
            }
        }
    }

    override val graphView: List<GraphView>
        get() {
            val graphNodes = IdentityHashMap<Node, GraphView>()

            for (node in nodes) {
                val label = node.statements.joinToString("\n") { it.predicate.print() }
                graphNodes[node] = GraphView(System.identityHashCode(node).toString(), label)
            }

            for (node in nodes) {
                val current = graphNodes.getValue(node)
                for ((clause, child) in node.downEdges) {
                    current.addSuccessor(graphNodes.getValue(child), clause.predicate.print())
                }
            }

            return graphNodes.values.toList()
        }
}