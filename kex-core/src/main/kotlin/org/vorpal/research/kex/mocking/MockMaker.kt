package org.vorpal.research.kex.mocking

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.manager.instantiationManager
import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.descriptor.MockDescriptor
import org.vorpal.research.kex.descriptor.ObjectDescriptor
import org.vorpal.research.kex.descriptor.descriptor
import org.vorpal.research.kex.ktype.KexClass
import org.vorpal.research.kex.ktype.KexRtManager.isKexRt
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kex.util.loadClass
import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kfg.type.ClassType
import org.vorpal.research.kfg.type.TypeFactory
import org.vorpal.research.kfg.type.objectType

sealed class MockMaker(protected val ctx: ExecutionContext) {
    protected val types: TypeFactory = ctx.types

    abstract fun canMock(descriptor: Descriptor): Boolean
    abstract fun mockOrNull(descriptor: Descriptor): MockDescriptor?

    protected fun satisfiesNecessaryConditions(descriptor: Descriptor): Boolean {
        val klass = getKlass(descriptor) ?: return false
        return !klass.isFinal && !descriptor.type.isKexRt && descriptor is ObjectDescriptor
    }

    protected fun getKlass(descriptor: Descriptor): Class? =
        (descriptor.type.getKfgType(types) as? ClassType)?.klass
}

private class AllMockMaker(ctx: ExecutionContext) : MockMaker(ctx) {
    override fun canMock(descriptor: Descriptor): Boolean {
        return satisfiesNecessaryConditions(descriptor)
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
        return satisfiesNecessaryConditions(descriptor) &&
                !instantiationManager.isInstantiable(klass)
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
        }
        allAncestors.forEach { it.getFunctionalInterfaces(interfaces) }
        return interfaces
    }

    override fun canMock(descriptor: Descriptor): Boolean {
        val functionalInterfaces = getKlass(descriptor)?.getFunctionalInterfaces() ?: emptySet()
        return !descriptor.type.isKexRt && descriptor is ObjectDescriptor && functionalInterfaces.isNotEmpty()
    }

    override fun mockOrNull(descriptor: Descriptor): MockDescriptor? {
        if (!canMock(descriptor)) return null
        descriptor as ObjectDescriptor
        val klass = getKlass(descriptor) ?: return null
        val functionalInterfaces = klass.getFunctionalInterfaces()
        return when (functionalInterfaces.size) {
            0 -> null
            1 -> descriptor { mock(descriptor, functionalInterfaces.first().kexType) }
            else -> descriptor {
                mock(
                    descriptor,
                    ctx.types.objectType.kexType as KexClass,
                    functionalInterfaces.map { it.kexType }.toSet()
                )
            }
        } as? MockDescriptor
    }
}

fun createMocker(rule: MockingRule, ctx: ExecutionContext): MockMaker = when (rule) {
    MockingRule.LAMBDA -> LambdaMockMaker(ctx)
    MockingRule.ANY -> AllMockMaker(ctx)
    MockingRule.UNIMPLEMENTED -> UnimplementedMockMaker(ctx)
}
