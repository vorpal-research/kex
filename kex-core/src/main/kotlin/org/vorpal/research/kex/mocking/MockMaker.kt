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

// mock is subtype of original
interface MockMaker {
    fun canMock(descriptor: Descriptor, expectedClass: Class? = null): Boolean
    fun mockOrNull(original: Descriptor, expectedClass: Class? = null): MockDescriptor?
}

private sealed class AbstractMockMaker(protected val ctx: ExecutionContext) : MockMaker {
    protected val types: TypeFactory get() = ctx.types
    protected fun satisfiesNecessaryConditions(descriptor: Descriptor): Boolean {
        val klass = descriptor.kfgClass ?: return false
        return !klass.isFinal && !descriptor.type.isKexRt && descriptor is ObjectDescriptor
    }

    protected val Descriptor.kfgClass: Class?
        get() = (type.getKfgType(types) as? ClassType)?.klass
}

private class AllMockMaker(ctx: ExecutionContext) : AbstractMockMaker(ctx) {
    override fun canMock(descriptor: Descriptor, expectedClass: Class?): Boolean {
        return satisfiesNecessaryConditions(descriptor)
    }

    override fun mockOrNull(original: Descriptor, expectedClass: Class?): MockDescriptor? {
        if (!canMock(original)) return null
        original as ObjectDescriptor
        return MockDescriptor(original).also { it.fields.putAll(original.fields) }
    }
}

private class UnimplementedMockMaker(ctx: ExecutionContext) : AbstractMockMaker(ctx) {
    override fun canMock(descriptor: Descriptor, expectedClass: Class?): Boolean {
        val klass = (descriptor.type.getKfgType(types) as? ClassType)?.klass ?: return false
        return satisfiesNecessaryConditions(descriptor) &&
                !instantiationManager.isInstantiable(klass)
    }

    override fun mockOrNull(original: Descriptor, expectedClass: Class?): MockDescriptor? {
        if (!canMock(original)) return null
        original as ObjectDescriptor
        return MockDescriptor(original).also { it.fields.putAll(original.fields) }
    }
}

private const val FUNCTIONAL_INTERFACE_CLASS_NAME = "java/lang/FunctionalInterface"
private val Class.isLambda: Boolean
    get() = isInterface && annotations.any { it.type.name == FUNCTIONAL_INTERFACE_CLASS_NAME }

private class LambdaMockMaker(ctx: ExecutionContext) : AbstractMockMaker(ctx) {
    private fun Class.getFunctionalInterfaces(
        interfaces: MutableSet<Class> = mutableSetOf()
    ): Set<Class> {
        if (isLambda) {
            interfaces.add(this)
        }
        allAncestors.forEach { it.getFunctionalInterfaces(interfaces) }
        return interfaces
    }

    override fun canMock(descriptor: Descriptor, expectedClass: Class?): Boolean {
        return expectedClass?.isLambda == true && descriptor is ObjectDescriptor
    }

    override fun mockOrNull(original: Descriptor, expectedClass: Class?): MockDescriptor? {
        if (!canMock(original, expectedClass)) return null

        return descriptor { mock(original as ObjectDescriptor, expectedClass!!.kexType) }
    }
}


internal class CompositeMockMaker(private val mockMakers: List<MockMaker>) : MockMaker {
    override fun canMock(descriptor: Descriptor, expectedClass: Class?): Boolean {
        return mockMakers.any { mockMaker -> mockMaker.canMock(descriptor, expectedClass) }
    }

    override fun mockOrNull(original: Descriptor, expectedClass: Class?): MockDescriptor? {
        return mockMakers.firstNotNullOfOrNull { mockMaker ->
            mockMaker.mockOrNull(original, expectedClass)
        }
    }
}

private class ExcludingMockMaker(
    private val mockMaker: MockMaker,
    private val exclude: (Descriptor) -> Boolean
) : MockMaker {
    override fun canMock(descriptor: Descriptor, expectedClass: Class?): Boolean {
        return !exclude(descriptor) && mockMaker.canMock(descriptor, expectedClass)
    }

    override fun mockOrNull(original: Descriptor, expectedClass: Class?): MockDescriptor? {
        if (exclude(original)) return null
        return mockMaker.mockOrNull(original, expectedClass)
    }
}

fun MockMaker.excluding(excludePredicate: (Descriptor) -> Boolean): MockMaker =
    ExcludingMockMaker(this, excludePredicate)

fun createMockMaker(rule: MockingRule, ctx: ExecutionContext): MockMaker = when (rule) {
    MockingRule.LAMBDA -> LambdaMockMaker(ctx)
    MockingRule.ANY -> AllMockMaker(ctx)
    MockingRule.UNIMPLEMENTED -> UnimplementedMockMaker(ctx)
}
