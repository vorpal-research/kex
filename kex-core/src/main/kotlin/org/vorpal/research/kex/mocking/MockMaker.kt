package org.vorpal.research.kex.mocking

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.manager.instantiationManager
import org.vorpal.research.kex.config.Config
import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.descriptor.MockDescriptor
import org.vorpal.research.kex.descriptor.ObjectDescriptor
import org.vorpal.research.kex.ktype.KexRtManager.isKexRt
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.util.loadClass
import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kfg.type.ClassType
import org.vorpal.research.kfg.type.TypeFactory
import org.vorpal.research.kthelper.logging.log
import org.vorpal.research.kthelper.logging.warn

sealed class MockMaker(protected val ctx: ExecutionContext) {
    protected val types: TypeFactory = ctx.types

    abstract fun canMock(descriptor: Descriptor): Boolean
    abstract fun mockOrNull(descriptor: Descriptor): MockDescriptor?

    protected fun necessaryConditions(descriptor: Descriptor): Boolean {
        val klass = getKlass(descriptor) ?: return false
        return !klass.isFinal && !descriptor.type.isKexRt && descriptor is ObjectDescriptor
    }

    protected fun getKlass(descriptor: Descriptor): Class? =
        (descriptor.type.getKfgType(types) as? ClassType)?.klass
}

private class AllMockMaker(ctx: ExecutionContext) : MockMaker(ctx) {
    override fun canMock(descriptor: Descriptor): Boolean {
        return necessaryConditions(descriptor)
    }

    override fun mockOrNull(descriptor: Descriptor): MockDescriptor? {
        if (!canMock(descriptor)) return null
        descriptor as ObjectDescriptor
        return MockDescriptor(descriptor).also { it.fields.putAll(descriptor.fields) }
    }
}

private class UnimplementedMockMaker(ctx: ExecutionContext) : MockMaker(ctx) {
    override fun canMock(descriptor: Descriptor): Boolean {
        val klass = (descriptor.type.getKfgType(types) as? ClassType)?.klass ?: return false
        return necessaryConditions(descriptor) && !instantiationManager.isInstantiable(klass)
    }

    override fun mockOrNull(descriptor: Descriptor): MockDescriptor? {
        if (!canMock(descriptor)) return null
        descriptor as ObjectDescriptor
        return MockDescriptor(descriptor).also { it.fields.putAll(descriptor.fields) }
    }

}

private class LambdaMockMaker(ctx: ExecutionContext) : MockMaker(ctx) {

    private fun Class.getFunctionalInterfaces(
        interfaces: MutableSet<Class> = mutableSetOf()
    ): Set<Class> {
        if (this.isInterface &&
            ctx.loader.loadClass(this).getAnnotation(FunctionalInterface::class.java) != null
        ) {
            interfaces.add(this)
            return interfaces
        }
        allAncestors.forEach { it.getFunctionalInterfaces(interfaces) }
        return interfaces
    }

    override fun canMock(descriptor: Descriptor): Boolean {
        val klass = getKlass(descriptor) ?: return false
        return necessaryConditions(descriptor) && klass.getFunctionalInterfaces().isNotEmpty()
    }

    override fun mockOrNull(descriptor: Descriptor): MockDescriptor? {
        if (!canMock(descriptor)) return null
        descriptor as ObjectDescriptor
        val klass = getKlass(descriptor) ?: return null
        val functionalInterfaces = klass.getFunctionalInterfaces()
        return when (functionalInterfaces.size) {
            0 -> null
            1 -> org.vorpal.research.kex.descriptor.descriptor {
                mock(
                    descriptor,
                    functionalInterfaces.first().kexType
                )
            }
            // TODO create mock with all interfaces
            else -> org.vorpal.research.kex.descriptor.descriptor {
                mock(
                    descriptor,
                    functionalInterfaces.random().kexType
                )
            }.also {
                log.warn { "FIXME: Multiple functional interfaces, so chosen the random one :D" }
            }
        } as? MockDescriptor
    }
}

enum class MockingRule {
    // Order is important! First rule applies first
    LAMBDA, ANY, UNIMPLEMENTED
}

fun createMocker(rule: MockingRule, ctx: ExecutionContext): MockMaker = when (rule) {
    MockingRule.LAMBDA -> LambdaMockMaker(ctx)
    MockingRule.ANY -> AllMockMaker(ctx)
    MockingRule.UNIMPLEMENTED -> UnimplementedMockMaker(ctx)
}

fun Config.getMockMakers(ctx: ExecutionContext): List<MockMaker> =
    getMultipleStringValue("mock", "rule")
        .map { enumName -> MockingRule.valueOf(enumName.uppercase()) }
        .sortedBy { rule -> rule.ordinal }
        .map { rule -> createMocker(rule, ctx) }