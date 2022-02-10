package org.jetbrains.research.kex.smt

import org.apache.velocity.VelocityContext
import org.apache.velocity.app.VelocityEngine
import org.apache.velocity.runtime.RuntimeConstants
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader
import java.io.Writer

class ClassGenerator(
    private val parameters: Map<String, Any>,
    private val template: String
) {
    fun write(writer: Writer) {
        val engine = VelocityEngine()
        engine.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath")
        engine.setProperty("classpath.resource.loader.class", ClasspathResourceLoader::class.java.name)
        engine.init()

        val context = VelocityContext()
        parameters.forEach { (key, value) -> context.put(key, value) }

        val template = engine.getTemplate(template)

        template.merge(context, writer)
    }
}