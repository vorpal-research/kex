package org.jetbrains.research.kex.smtinstance

import org.apache.velocity.VelocityContext
import org.apache.velocity.app.VelocityEngine
import java.io.StringWriter

class ClassGenerator(val parameters: Map<String, String>, val template: String) {
    fun doit() {
        val engine = VelocityEngine()
        engine.init()

        val context = VelocityContext()
        parameters.forEach { key, value -> context.put(key, value) }

        val template = engine.getTemplate(template)

        val writer = StringWriter()
        template.merge(context, writer)
        println(writer)
    }
}