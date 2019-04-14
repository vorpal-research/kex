package org.jetbrains.research.kex.annotations

interface AnnotationsLoader {
    fun getCallOverloads(name: String): Sequence<AnnotatedCall>
    fun getExactCall(name: String, vararg params: String): AnnotatedCall? {
        return getCallOverloads(name).find {
            if (params.size == it.params.size) {
                for (i in 0 until params.size)
                    if (params[i] != it.params[i].type)
                        return@find false
                true
            } else
                false
        }
    }
}
