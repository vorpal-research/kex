package org.jetbrains.research.kex.smt

import org.jetbrains.research.kex.state.term.Term

data class MemoryShape(val initialMemory: Map<Term, Term>, val finalMemory: Map<Term, Term>)

data class SMTModel(val assignments: Map<Term, Term>, val memories: Map<Int, MemoryShape>, val bounds: Map<Int, MemoryShape>) {
    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendln("Model {")
        assignments.forEach { key, value -> sb.appendln("\t$key = $value") }
        memories.forEach { memspace, memory ->
            memory.finalMemory.forEach { key, value ->
                sb.appendln("\t($key)<$memspace> = $value")
            }
        }
        bounds.forEach { memspace, memory ->
            memory.finalMemory.forEach { key, value ->
                sb.appendln("\tbound($key)<$memspace> = $value")
            }
        }
        sb.append("}")
        return sb.toString()
    }
}