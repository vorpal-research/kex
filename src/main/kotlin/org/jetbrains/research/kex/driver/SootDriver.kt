package org.jetbrains.research.kex.driver

import org.jetbrains.research.kex.config.GlobalConfig
import org.jetbrains.research.kex.util.loggerFor
import soot.*
import soot.options.Options

class SootDriver private constructor() {
    private val log = loggerFor(SootDriver.javaClass)
    private val config = GlobalConfig.instance

    fun setup(jarPackage: String, mainClass: String) {
        log.debug("Setting up soot with jar = $jarPackage and main class $mainClass")
        G.reset()
        Options.v().set_via_shimple(true)
        Options.v().set_keep_line_number(true)
        Options.v().set_keep_offset(true)
        Options.v().set_app(true)
        Options.v().set_whole_program(true)
        Options.v().set_allow_phantom_refs(true)

        val additionalOpts = config.getMultipleStringValue("soot-options", " ")
        log.debug("Soot additional options: '$additionalOpts'")
        Options.v().parse(additionalOpts.toTypedArray())
        Scene.v().loadBasicClasses()

        val cp = config.getMultipleStringValue("soot-classpath").joinToString(":")
        Scene.v().sootClassPath = "$cp:$jarPackage"

        val mainClass = Scene.v().loadClassAndSupport(mainClass)
        mainClass.setApplicationClass()
        Scene.v().loadNecessaryClasses()
        Scene.v().loadDynamicClasses()

        PackManager.v().runPacks()
        log.debug("Soot configured")
    }

    fun getClass(name: String): SootClass = Scene.v().loadClassAndSupport(name)

    fun getAllClasses(): Collection<SootClass> = Scene.v().classes
    fun getClasses(): Collection<SootClass> =
            Scene.v().classes.filter { !it.name.matches(Regex("java.*|kotlin.*|sun.*|jdk.*")) }

    fun getApplicationClasses(): Collection<SootClass> =
            Scene.v().applicationClasses

    private object Holder { val INSTANCE = SootDriver() }

    companion object {
        val instance: SootDriver by lazy { Holder.INSTANCE }
    }
}