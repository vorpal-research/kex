package org.jetbrains.research.kex.state.transformer

import org.jetbrains.research.kex.asm.state.PredicateStateAnalysis
import org.jetbrains.research.kex.descriptor.concrete
import org.jetbrains.research.kex.descriptor.concreteClass
import org.jetbrains.research.kex.ktype.KexType
import org.jetbrains.research.kex.ktype.kexType
import org.jetbrains.research.kex.state.predicate.*
import org.jetbrains.research.kex.state.term.*
import org.jetbrains.research.kfg.type.ClassType
import org.jetbrains.research.kthelper.algorithm.GraphView
import org.jetbrains.research.kthelper.algorithm.Viewable

class MustAliasGraph(

) : Viewable {
    private val nodes = mutableSetOf<MustAliasNode>()
    private val varToNode = mutableMapOf<Term, MustAliasNode>()

    fun addNode(node: MustAliasNode) {
        nodes.add(node)
        for (variable in node.variables) {
            varToNode[variable] = node
        }
    }

    fun removeNode(node: MustAliasNode) {
        nodes.remove(node)
        for (variable in node.variables) {
            varToNode.remove(variable)
        }

        for ((variable, toNode) in node.getIncomingNodesMap()) {
            toNode.removeOutEdge(variable)
        }
        for ((variable, outNode) in node.getOutgoingNodesMap()) {
            outNode.removeInEdge(variable)
        }
    }

    fun addVariableToNode(node: MustAliasNode, variable: Term) {
        node.addVariable(variable)
        varToNode[variable] = node
    }

    fun removeVariableFromNode(node: MustAliasNode, variable: Term) {
        node.removeVariable(variable)
        varToNode.remove(variable)
    }

    fun getNodes(): MutableSet<MustAliasNode> {
        return nodes
    }

    fun lookupVariable(variable: Term): MustAliasNode? {
        return varToNode[variable]
    }

    fun gcNodes() {
        var changed = false
        while (!changed) {
            val nodesToBeRemoved = mutableSetOf<MustAliasNode>()
            for (node in nodes) {
                val varsCount = node.variables.size
                val outgoingCount = node.getOutgoingNodesMap().size
                val incomingCount = node.getIncomingNodesMap().size

                if (
                    (varsCount == 1 && outgoingCount + incomingCount == 0)
                    || varsCount == 0 && incomingCount == 0
                    || varsCount == 0 && incomingCount == 1 && outgoingCount == 0
                ) {
                    nodesToBeRemoved.add(node)
                    changed = true
                }
            }

            nodes.removeAll(nodesToBeRemoved)
        }
    }

    internal fun intersectNode(
        node1: MustAliasNode,
        node2: MustAliasNode,
        newNodes: Map<Pair<MustAliasNode, MustAliasNode>, MustAliasNode>
    ) {
        val newNode = newNodes[node1 to node2]!!
        for ((variable, oldNextNode1) in node1.getOutgoingNodesMap().entries) {
            val oldNextNode2 = node2.outgoingNodes[variable]
            if (oldNextNode2 != null) {
                val newNextNode = newNodes[oldNextNode1 to oldNextNode2]!!
                newNode.addOutEdge(variable, newNextNode)
            }
        }
    }

    companion object {
        fun intersect(graph1: MustAliasGraph, graph2: MustAliasGraph): MustAliasGraph {
            val newNodes = mutableMapOf<Pair<MustAliasNode, MustAliasNode>, MustAliasNode>()
            val newGraph = MustAliasGraph()
            for (oldNode1 in graph1.nodes) {
                for (oldNode2 in graph2.nodes) {
                    val newNode = MustAliasNode.intersectVariables(oldNode1, oldNode2)
                    newNodes[oldNode1 to oldNode2] = newNode
                    newGraph.addNode(newNode)
                }
            }

            for (oldNode1 in graph1.nodes) {
                for (oldNode2 in graph2.nodes) {
                    newGraph.intersectNode(oldNode1, oldNode2, newNodes)
                }
            }

            newGraph.gcNodes()
            return newGraph
        }
    }

    override val graphView: List<GraphView>
        get() {
            val res = mutableListOf<GraphView>()
            for ((key, value) in varToNode) {
                val terms = GraphView("term:", key.name)
                val obj = GraphView("value", value.variables.joinToString(separator = ", ") { it.name })
                terms.addSuccessor(obj)
                res.add(terms)
                res.add(obj)
            }

            return res
        }
}

class MustAliasNode(
    val context: MustAliasAnalysisContext
) {
    val variables = mutableSetOf<Term>()
    val incomingNodes = mutableMapOf<Term, MustAliasNode>()
    val outgoingNodes = mutableMapOf<Term, MustAliasNode>()

    fun addVariable(term: Term) {
        variables.add(term)
    }

    fun removeVariable(term: Term) {
        variables.remove(term)
    }

    fun getIncomingNodesMap(): Map<Term, MustAliasNode> {
        return incomingNodes
    }

    fun getOutgoingNodesMap(): Map<Term, MustAliasNode> {
        return outgoingNodes
    }

    fun getIncomingNodes(): Set<MustAliasNode> {
        return incomingNodes.values.toSet()
    }

    fun getOutgoingNodes(): Set<MustAliasNode> {
        return outgoingNodes.values.toSet()
    }

    fun addOutEdge(variable: Term, node: MustAliasNode) {
        outgoingNodes[variable] = node
        node.incomingNodes[variable] = node
    }

    fun removeOutEdge(variable: Term) {
        val toNode = outgoingNodes.remove(variable)
        toNode?.incomingNodes?.remove(variable)
    }

    fun removeInEdge(variable: Term) {
        val toNode = incomingNodes.remove(variable)
        toNode?.outgoingNodes?.remove(variable)
    }

    fun cloneVarsOnly(): MustAliasNode {
        return MustAliasNode(MustAliasAnalysisContext()).apply {
            variables.addAll(this@MustAliasNode.variables)
        }
    }

    companion object {
        fun intersectVariables(node1: MustAliasNode, node2: MustAliasNode): MustAliasNode {
            return MustAliasNode(MustAliasAnalysisContext()).apply {
                val intersectedVars = node1.variables.intersect(node2.variables).toMutableSet()
                variables.addAll(intersectedVars)
            }
        }
    }
}

class MustAliasAnalysisContext {
    var klass: org.jetbrains.research.kfg.ir.Class? = null
}

class MustAliasAnalysis(
    val toMap: Map<Term, KexType>,
    private val psa: PredicateStateAnalysis
) : Transformer<MustAliasAnalysis> {
    private val cm = psa.cm
    val graph = MustAliasGraph()

    override fun transformFieldLoadTerm(term: FieldLoadTerm): Term {
        val node = MustAliasNode(MustAliasAnalysisContext())
        graph.addNode(node)
        graph.addVariableToNode(node, term.field)
        return term
    }

    override fun transformEqualityPredicate(predicate: EqualityPredicate): Predicate {
        val node = graph.lookupVariable(predicate.rhv)
        if (node != null) {
            graph.addVariableToNode(node, predicate.lhv)
        }
        return predicate
    }

    override fun transformCallPredicate(predicate: CallPredicate): Predicate {
        if (!predicate.hasLhv) {
            return predicate
        }
        val callee = predicate.call as? CallTerm ?: return predicate
        val context = MustAliasAnalysisContext().apply {
            klass = callee.method.klass
        }
        val node = MustAliasNode(context)
        graph.addNode(node)
        graph.addVariableToNode(node, predicate.lhv)

        return predicate
    }

    override fun transformNewPredicate(predicate: NewPredicate): Predicate {
        val context = MustAliasAnalysisContext().apply {
            klass = (predicate.lhv.type.getKfgType(cm.type) as? ClassType)?.klass ?: return predicate
        }
        val node = MustAliasNode(context)
        graph.addNode(node)
        graph.addVariableToNode(node, predicate.lhv)

        return predicate
    }
}