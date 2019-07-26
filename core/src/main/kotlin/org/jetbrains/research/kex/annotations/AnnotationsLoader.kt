package org.jetbrains.research.kex.annotations

interface AnnotationsLoader {
    fun getCallOverloads(name: String): Sequence<AnnotatedCall>
    fun getExactCall(name: String, vararg params: String) = getCallOverloads(name).find {
        when {
            params.size == it.params.size -> params.withIndex().all { (index, param) ->  param == it.params[index].type }
            else -> false
        }
    }
}
