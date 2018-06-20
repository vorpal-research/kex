package org.jetbrains.research.kex.driver

import org.apache.velocity.VelocityContext
import org.apache.velocity.app.VelocityEngine
import org.apache.velocity.runtime.RuntimeConstants
import org.jetbrains.research.kex.config.GlobalConfig
import org.jetbrains.research.kex.util.Loggable
import org.jetbrains.research.kex.util.loggerFor
import org.jetbrains.research.kex.util.unreachable
import java.io.StringWriter

val templatePath = GlobalConfig.getStringValue("template.root-directory") ?: unreachable {
    loggerFor("errs").error("Specify path to templates directory")
}

class TemplateDriver : Loggable {


    fun run() {
        log.debug(templatePath)
        val engine = VelocityEngine()
        engine.setProperty(RuntimeConstants.FILE_RESOURCE_LOADER_PATH, templatePath)
        engine.init()

        val context = VelocityContext()
        context.put("packageName", "org.jetbrains.research.kex")
        context.put("className", "HelloWorld")
        context.put("userName", "lol")

        val template = engine.getTemplate("helloWorld.vm")

        val writew = StringWriter()
        template.merge(context, writew)
        println(writew)
    }
}