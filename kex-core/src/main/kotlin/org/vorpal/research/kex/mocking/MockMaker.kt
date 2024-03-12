package org.vorpal.research.kex.mocking

import org.vorpal.research.kex.ExecutionContext
import org.vorpal.research.kex.asm.manager.instantiationManager
import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.descriptor.MockDescriptor
import org.vorpal.research.kex.descriptor.ObjectDescriptor
import org.vorpal.research.kex.descriptor.descriptor
import org.vorpal.research.kex.ktype.KexRtManager.isKexRt
import org.vorpal.research.kex.ktype.kexType
import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kfg.type.ClassType
import org.vorpal.research.kfg.type.TypeFactory

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

private const val FUNCTIONAL_INTERFACE_CLASS_NAME = "java/lang/FunctionalInterface"
private val Class.isLambda: Boolean
    get() = isInterface && annotations.any { it.type.name == FUNCTIONAL_INTERFACE_CLASS_NAME }

private class LambdaMockMaker(ctx: ExecutionContext) : MockMaker(ctx) {
    private fun Class.getFunctionalInterfaces(
        interfaces: MutableSet<Class> = mutableSetOf()
    ): Set<Class> {
        if (isLambda) {
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
        if (klass.isLambda) {
            return descriptor { mock(klass.kexType) }
        }
        val mockKlass = (if (klass.isFinal) klass.superClass else klass) ?: return null
        val functionalInterfaces = klass.getFunctionalInterfaces()

        if (functionalInterfaces.isEmpty()) return null
        return descriptor {
            mock(descriptor, mockKlass.kexType, functionalInterfaces.map { it.kexType }.toSet())
        }
    }
}

fun createMocker(rule: MockingRule, ctx: ExecutionContext): MockMaker = when (rule) {
    MockingRule.LAMBDA -> LambdaMockMaker(ctx)
    MockingRule.ANY -> AllMockMaker(ctx)
    MockingRule.UNIMPLEMENTED -> UnimplementedMockMaker(ctx)
}
