package org.jetbrains.research.kex

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode
import org.jetbrains.research.kex.asm.CoverageTransformer
import org.jetbrains.research.kex.config.CmdConfig
import org.jetbrains.research.kex.config.GlobalConfig
import org.jetbrains.research.kex.config.FileConfig
import org.jetbrains.research.kex.util.loggerFor
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.util.CheckClassAdapter
import uk.org.lidalia.sysoutslf4j.context.LogLevel
import uk.org.lidalia.sysoutslf4j.context.SysOutOverSLF4J
import java.io.FileOutputStream
import java.util.jar.JarFile
import java.net.URL


fun main(args: Array<String>) {
    SysOutOverSLF4J.sendSystemOutAndErrToSLF4J(LogLevel.DEBUG, LogLevel.ERROR)
    val log = loggerFor("org.jetbrains.research.MainKt")

    val cmd = CmdConfig(args)
    val propertiesFile = cmd.getStringValue("properties", "system.properties")
    GlobalConfig.initialize( listOf(cmd, FileConfig(propertiesFile)) )

    val jarName = GlobalConfig.instance.getStringValue("jar", "")
    val packageName = GlobalConfig.instance.getStringValue("package", "")
    assert(jarName.isEmpty().or(packageName.isEmpty()), cmd::printHelp)
    System.setProperty("java.class.path", "${System.getProperty("java.class.path")}:$jarName")

    val jarFile = JarFile(jarName)
    val entries = jarFile.entries()
    while (entries.hasMoreElements()) {
        val entry = entries.nextElement()
        if (entry.name.endsWith(".class")) {
            val realName = entry.name.removeSuffix(".class").replace("/", ".")
            if (!realName.contains(packageName)) continue

            val url = URL("jar:file:$jarName!/${entry.name}")
            val cn = ClassNode()
            val cr = ClassReader(url.openStream())
            cr.accept(cn, 0)

            cn.methods.forEach { it ->
                CoverageTransformer().transform(it as MethodNode)
            }

            val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
            val cca = CheckClassAdapter(cw)
            cn.accept(cca)

            val fos = FileOutputStream("${realName.split('.').last()}.class")
            fos.write(cw.toByteArray())
            fos.close()
        }
    }
}