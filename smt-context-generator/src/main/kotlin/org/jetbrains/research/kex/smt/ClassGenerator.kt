package org.jetbrains.research.kex.smt

import org.apache.velocity.VelocityContext
import org.apache.velocity.app.VelocityEngine
import org.apache.velocity.runtime.RuntimeConstants
import java.io.Writer

class ClassGenerator(val parameters: Map<String, Any>, val path: String, val template: String) {
    fun doit(writer: Writer) {
        val engine = VelocityEngine()
        engine.setProperty(RuntimeConstants.FILE_RESOURCE_LOADER_PATH, path)
        engine.init()

        val context = VelocityContext()
        parameters.forEach { key, value -> context.put(key, value) }

        val template = engine.getTemplate(template)

        template.merge(context, writer)
    }
}