package org.vorpal.research.kex.smt

import org.vorpal.research.kex.ktype.KexType
import org.vorpal.research.kex.state.term.Term

data class MemoryShape(val initialMemory: Map<Term, Term>, val finalMemory: Map<Term, Term>)

data class SMTModel(
    val assignments: Map<Term, Term>,
    val memories: Map<Int, MemoryShape>,
    val properties: Map<Int, Map<String, MemoryShape>>,
    val arrays: Map<Int, Map<Term, MemoryShape>>,
    val strings: Map<Int, MemoryShape>,
    val typeMap: Map<Term, KexType>,
    val hasStrings: Boolean
) {
    constructor(
        assignments: Map<Term, Term>,
        memories: Map<Int, MemoryShape>,
        properties: Map<Int, Map<String, MemoryShape>>,
        arrays: Map<Int, Map<Term, MemoryShape>>,
        strings: Map<Int, MemoryShape>,
        typeMap: Map<Term, KexType>
    ) : this(
        assignments,
        memories,
        properties,
        arrays,
        strings,
        typeMap,
        true
    )

    constructor(
        assignments: Map<Term, Term>,
        memories: Map<Int, MemoryShape>,
        properties: Map<Int, Map<String, MemoryShape>>,
        arrays: Map<Int, Map<Term, MemoryShape>>,
        typeMap: Map<Term, KexType>
    ) : this(
        assignments,
        memories,
        properties,
        arrays,
        mapOf(),
        typeMap,
        false
    )

    constructor(
        assignments: Map<Term, Term>,
        memories: Map<Int, MemoryShape>,
        properties: Map<Int, Map<String, MemoryShape>>,
        typeMap: Map<Term, KexType>
    ) : this(
        assignments,
        memories,
        properties,
        mapOf(),
        mapOf(),
        typeMap,
        false
    )

    override fun toString() = buildString {
        appendLine("Model {")
        assignments.forEach { (key, value) -> appendLine("\t$key = $value") }
        memories.forEach { (memspace, memory) ->
            memory.finalMemory.forEach { (key, value) ->
                appendLine("\t($key)<$memspace> = $value")
            }
        }
        properties.forEach { (memspace, map) ->
            map.forEach { (name, memory) ->
                memory.finalMemory.forEach { (key, value) ->
                    appendLine("\t$name($key)<$memspace> = $value")
                }
            }
        }
        append("}")
    }
}