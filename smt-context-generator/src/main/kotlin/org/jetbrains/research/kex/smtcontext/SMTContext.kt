package org.jetbrains.research.kex.smtcontext

import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.TypeElement

annotation class SMTContext(val solver: String, val context: String)

class SMTContextProcessor() : AbstractProcessor() {
    override fun process(annotations: MutableSet<out TypeElement>?, roundEnv: RoundEnvironment?): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}