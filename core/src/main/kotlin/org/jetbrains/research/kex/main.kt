package org.jetbrains.research.kex

import kotlinx.serialization.ImplicitReflectionSerializer

@ImplicitReflectionSerializer
fun main(args: Array<String>) {
    Kex(args).main()
}