package org.jetbrains.research.kex.smt

import org.jetbrains.research.kex.state.term.Term

data class MemoryShape(val initialMemory: Map<Term, Term>, val finalMemory: Map<Term, Term>)

data class SMTModel(val assignments: Map<Term, Term>, val memories: Map<Int, MemoryShape>, val bounds: Map<Int, MemoryShape>) {
    override fun toString() = buildString {
        appendln("Model {")
        assignments.forEach { (key, value) -> appendln("\t$key = $value") }
        memories.forEach { (memspace, memory) ->
            memory.finalMemory.forEach { (key, value) ->
                appendln("\t($key)<$memspace> = $value")
            }
        }
        bounds.forEach { (memspace, memory) ->
            memory.finalMemory.forEach { (key, value) ->
                appendln("\tbound($key)<$memspace> = $value")
            }
        }
        append("}")
    }
}

data class PropertyModel(
        val assignments: Map<Term, Term>,
        val memories: Map<Int, MemoryShape>,
        val properties: Map<Int, Map<String, MemoryShape>>
) {
    override fun toString() = buildString {
        appendln("Model {")
        assignments.forEach { (key, value) -> appendln("\t$key = $value") }
        memories.forEach { (memspace, memory) ->
            memory.finalMemory.forEach { (key, value) ->
                appendln("\t($key)<$memspace> = $value")
            }
        }
        properties.forEach { (memspace, map) ->
            map.forEach { (name, memory) ->
                memory.finalMemory.forEach { (key, value) ->
                    appendln("\t$name($key)<$memspace> = $value")
                }
            }
        }
        append("}")
    }
}