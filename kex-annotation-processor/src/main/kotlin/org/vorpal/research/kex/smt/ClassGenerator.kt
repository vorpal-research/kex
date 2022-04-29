package org.vorpal.research.kex.smt

import org.apache.velocity.VelocityContext
import org.apache.velocity.app.VelocityEngine
import org.apache.velocity.runtime.RuntimeConstants
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader
import org.slf4j.Logger
import org.slf4j.Marker
import java.io.Writer

internal class NullLogger : Logger {
    override fun getName(): String = "null"
    override fun isTraceEnabled(): Boolean = false
    override fun isTraceEnabled(p0: Marker?): Boolean = false
    override fun trace(p0: String?) {}
    override fun trace(p0: String?, p1: Any?) {}
    override fun trace(p0: String?, p1: Any?, p2: Any?) {}
    override fun trace(p0: String?, vararg p1: Any?) {}
    override fun trace(p0: String?, p1: Throwable?) {}
    override fun trace(p0: Marker?, p1: String?) {}
    override fun trace(p0: Marker?, p1: String?, p2: Any?) {}
    override fun trace(p0: Marker?, p1: String?, p2: Any?, p3: Any?) {}
    override fun trace(p0: Marker?, p1: String?, vararg p2: Any?) {}
    override fun trace(p0: Marker?, p1: String?, p2: Throwable?) {}
    override fun isDebugEnabled(): Boolean = false
    override fun isDebugEnabled(p0: Marker?): Boolean = false
    override fun debug(p0: String?) {}
    override fun debug(p0: String?, p1: Any?) {}
    override fun debug(p0: String?, p1: Any?, p2: Any?) {}
    override fun debug(p0: String?, vararg p1: Any?) {}
    override fun debug(p0: String?, p1: Throwable?) {}
    override fun debug(p0: Marker?, p1: String?) {}
    override fun debug(p0: Marker?, p1: String?, p2: Any?) {}
    override fun debug(p0: Marker?, p1: String?, p2: Any?, p3: Any?) {}
    override fun debug(p0: Marker?, p1: String?, vararg p2: Any?) {}
    override fun debug(p0: Marker?, p1: String?, p2: Throwable?) {}
    override fun isInfoEnabled(): Boolean  = false
    override fun isInfoEnabled(p0: Marker?): Boolean = false
    override fun info(p0: String?) {}
    override fun info(p0: String?, p1: Any?) {}
    override fun info(p0: String?, p1: Any?, p2: Any?) {}
    override fun info(p0: String?, vararg p1: Any?) {}
    override fun info(p0: String?, p1: Throwable?) {}
    override fun info(p0: Marker?, p1: String?) {}
    override fun info(p0: Marker?, p1: String?, p2: Any?) {}
    override fun info(p0: Marker?, p1: String?, p2: Any?, p3: Any?) {}
    override fun info(p0: Marker?, p1: String?, vararg p2: Any?) {}
    override fun info(p0: Marker?, p1: String?, p2: Throwable?) {}
    override fun isWarnEnabled(): Boolean = false
    override fun isWarnEnabled(p0: Marker?): Boolean = false
    override fun warn(p0: String?) {}
    override fun warn(p0: String?, p1: Any?) {}
    override fun warn(p0: String?, vararg p1: Any?) {}
    override fun warn(p0: String?, p1: Any?, p2: Any?) {}
    override fun warn(p0: String?, p1: Throwable?) {}
    override fun warn(p0: Marker?, p1: String?) {}
    override fun warn(p0: Marker?, p1: String?, p2: Any?) {}
    override fun warn(p0: Marker?, p1: String?, p2: Any?, p3: Any?) {}
    override fun warn(p0: Marker?, p1: String?, vararg p2: Any?) {}
    override fun warn(p0: Marker?, p1: String?, p2: Throwable?) {}
    override fun isErrorEnabled(): Boolean = false
    override fun isErrorEnabled(p0: Marker?): Boolean = false
    override fun error(p0: String?) {}
    override fun error(p0: String?, p1: Any?) {}
    override fun error(p0: String?, p1: Any?, p2: Any?) {}
    override fun error(p0: String?, vararg p1: Any?) {}
    override fun error(p0: String?, p1: Throwable?) {}
    override fun error(p0: Marker?, p1: String?) {}
    override fun error(p0: Marker?, p1: String?, p2: Any?) {}
    override fun error(p0: Marker?, p1: String?, p2: Any?, p3: Any?) {}
    override fun error(p0: Marker?, p1: String?, vararg p2: Any?) {}
    override fun error(p0: Marker?, p1: String?, p2: Throwable?) {}
}

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