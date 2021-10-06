package org.jetbrains.research.kex.random

import org.jetbrains.research.kthelper.tryOrNull
import java.lang.reflect.Type

abstract class RandomizerError : Exception {
    constructor(msg: String) : super(msg)
    constructor(e: Throwable) : super(e)
}

class GenerationException : RandomizerError {
    constructor(msg: String) : super(msg)
    constructor(e: Throwable) : super(e)
}

class UnknownTypeException(msg: String) : RandomizerError(msg)

interface Randomizer {
    /**
     * @return generated object or throws #RandomizerError
     */
    fun next(type: Type): Any?

    /**
     * @return generated object or #null if any exception has occurred
     */
    fun nextOrNull(type: Type) = tryOrNull { next(type) }
}


