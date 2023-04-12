package org.vorpal.research.kex.random

import org.vorpal.research.kthelper.tryOrNull
import java.lang.reflect.Type
import kotlin.random.Random

abstract class RandomizerException : Exception {
    constructor(msg: String) : super(msg)
    constructor(e: Throwable) : super(e)
}

class GenerationException : RandomizerException {
    constructor(msg: String) : super(msg)
    constructor(e: Throwable) : super(e)
}

class UnknownTypeException(msg: String) : RandomizerException(msg)

abstract class Randomizer : Random() {
    /**
     * @return generated object or throws #RandomizerError
     */
    abstract fun next(type: Type): Any?

    /**
     * @return generated object or #null if any exception has occurred
     */
    fun nextOrNull(type: Type) = tryOrNull { next(type) }
}


