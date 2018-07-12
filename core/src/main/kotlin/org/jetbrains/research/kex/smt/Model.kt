package org.jetbrains.research.kex.smt

import org.jetbrains.research.kex.state.term.Term

class MemoryShape(val initialMemory: Map<Term, Term>, val finalMemory: Map<Term, Term>)

class SMTModel(val assignments: Map<Term, Term>, val memories: Map<Int, MemoryShape>, val bounds: Map<Int, MemoryShape>)