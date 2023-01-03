package org.vorpal.research.kex.asm.analysis.util

interface SuspendableIterator<T>  {
    suspend fun hasNext(): Boolean
    suspend fun next(): T
}
