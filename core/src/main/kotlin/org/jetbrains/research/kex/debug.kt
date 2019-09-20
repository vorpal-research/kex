package org.jetbrains.research.kex

import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.UnstableDefault

@UnstableDefault
@ImplicitReflectionSerializer
fun main(args: Array<String>) {
    Kex(args).debug()
}