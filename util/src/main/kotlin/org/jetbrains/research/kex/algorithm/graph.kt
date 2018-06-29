package org.jetbrains.research.kex.algorithm

import info.leadinglight.jdot.Edge
import info.leadinglight.jdot.Graph
import info.leadinglight.jdot.Node
import info.leadinglight.jdot.enums.Shape
import info.leadinglight.jdot.impl.Util
import org.jetbrains.research.kex.util.defaultHashCode
import java.io.File
import java.nio.file.Files
import java.util.*

interface GraphNode<out T : Any> {
    fun getPredSet(): Set<T>
    fun getSuccSet(): Set<T>
}

///////////////////////////////////////////////////////////////////////////////

class TopologicalSorter<T : GraphNode<T>>(val nodes: Set<T>) {
    private val order = mutableListOf<T>()
    private val cycled = mutableSetOf<T>()
    private val colors = mutableMapOf<T, Color>()

    private enum class Color { WHITE, GREY, BLACK }

    private fun dfs(node: T) {
        if (colors.getOrPut(node, { Color.WHITE }) == Color.BLACK) return
        if (colors[node]!! == Color.GREY) {
            cycled.add(node)
            return
        }
        colors[node] = Color.GREY
        for (edge in node.getSuccSet())
            dfs(edge)
        colors[node] = Color.BLACK
        order.add(node)
    }

    fun sort(node: T): Pair<List<T>, Set<T>> {
        assert(node in nodes)
        dfs(node)
        return Pair(order, cycled)
    }
}

///////////////////////////////////////////////////////////////////////////////

class LoopDetector<T : GraphNode<T>>(val nodes: Set<T>) {
    fun search(): Map<T, List<T>> {
        val tree = DominatorTreeBuilder(nodes).build()
        val backEdges = mutableListOf<Pair<T, T>>()
        for ((current, _) in tree) {
            for (succ in current.getSuccSet()) {
                val succTreeNode = tree.getValue(succ)
                if (succTreeNode.dominates(current)) {
                    backEdges.add(succ to current)
                }
            }
        }
        val result = mutableMapOf<T, MutableList<T>>()
        for ((header, end) in backEdges) {
            val body = mutableListOf(header)
            val stack = Stack<T>()
            stack.push(end)
            while (stack.isNotEmpty()) {
                val top = stack.pop()
                if (top !in body) {
                    body.add(top)
                    top.getPredSet().forEach { stack.push(it) }
                }
            }
            result.getOrPut(header, { mutableListOf() }).addAll(body)
        }
        return result
    }
}

///////////////////////////////////////////////////////////////////////////////

class GraphView(
        val name: String,
        val label: String,
        val successors: MutableList<GraphView>
) {
    constructor(name: String, label: String) : this(name, label, mutableListOf())

    override fun hashCode() = defaultHashCode(name)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (this.javaClass != other?.javaClass) return false
        other as GraphView
        return this.name == other.name
    }
}

fun viewCfg(name: String, nodes: List<GraphView>, dot: String = "/usr/bin/dot", browser: String = "/usr/bin/chromium") {
    Graph.setDefaultCmd("/usr/bin/dot")
    val graph = Graph(name)
    graph.addNodes(*nodes.map {
        Node(it.name).setShape(Shape.box).setLabel(it.label).setFontSize(12.0)
    }.toTypedArray())

    nodes.forEach {
        for (succ in it.successors) {
            graph.addEdge(Edge(it.name, succ.name))
        }
    }
    val file = graph.dot2file("svg")
    val newFile = "${file.removeSuffix("out")}svg"
    Files.move(File(file).toPath(), File(newFile).toPath())
    Util.sh(arrayOf(browser).plus("file://$newFile"))
}