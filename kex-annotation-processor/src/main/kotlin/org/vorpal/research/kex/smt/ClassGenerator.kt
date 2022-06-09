package org.vorpal.research.kex.smt

import org.apache.velocity.VelocityContext
import org.apache.velocity.app.VelocityEngine
import org.apache.velocity.runtime.RuntimeConstants
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader
import org.slf4j.Logger
import org.slf4j.Marker
import org.vorpal.research.kthelper.logging.NullLogger
import java.io.Writer

class ClassGenerator(
    private val parameters: Map<String, Any>,
    private val template: String
) {
    fun write(writer: Writer) {
        val engine = VelocityEngine()
        engine.setProperty(RuntimeConstants.RUNTIME_LOG_INSTANCE, NullLogger())
        engine.setProperty(RuntimeConstants.RESOURCE_LOADERS, "classpath")
        engine.setProperty("resource.loader.classpath.class", ClasspathResourceLoader::class.java.name)
        engine.init()

        val context = VelocityContext()
        parameters.forEach { (key, value) -> context.put(key, value) }

        val template = engine.getTemplate(template)

        template.merge(context, writer)
    }
}