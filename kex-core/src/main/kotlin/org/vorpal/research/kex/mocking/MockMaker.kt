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

interface MockMaker {
    fun canMock(descriptor: Descriptor, expectedClass: Class? = null): Boolean
    fun mockOrNull(original: Descriptor, expectedClass: Class? = null): MockDescriptor?
}

private fun Class.canMock(): Boolean =
    !isFinal && !isPrivate && !isKexRt && !(!isPublic && pkg.concreteName.startsWith("java"))

private sealed class AbstractMockMaker(protected val ctx: ExecutionContext) : MockMaker {
    protected fun satisfiesNecessaryConditions(descriptor: Descriptor): Boolean {
        val klass = descriptor.kfgClass ?: return false
        return klass.canMock() && !descriptor.type.isKexRt && descriptor is ObjectDescriptor
    }

    protected val Descriptor.kfgClass: Class?
        get() = kfgClass(ctx.types)
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

private class LambdaMockMaker(ctx: ExecutionContext) : AbstractMockMaker(ctx) {
    override fun canMock(descriptor: Descriptor, expectedClass: Class?): Boolean {
        return expectedClass?.isLambda == true && descriptor is ObjectDescriptor
    }

    override fun mockOrNull(original: Descriptor, expectedClass: Class?): MockDescriptor? {
        if (!canMock(original, expectedClass)) return null
        original as ObjectDescriptor

        var mockKlass = original.kfgClass!!
        val interfaces = mutableSetOf(expectedClass!!)
        while (!mockKlass.canMock()) {
            interfaces.addAll(mockKlass.interfaces)
            mockKlass = mockKlass.superClass!!
        }
        interfaces.remove(mockKlass)
        interfaces.removeIf { klass -> !klass.canMock() }
        return descriptor {
            mock(original, mockKlass.kexType, interfaces.map { it.kexType }.toSet())
        }
    }
}


private class CompositeMockMaker(private val mockMakers: List<MockMaker>) : MockMaker {
    constructor(vararg mockMakers: MockMaker) : this(mockMakers.toList())

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

fun MockMaker.filterNot(excludePredicate: (Descriptor) -> Boolean): MockMaker =
    ExcludingMockMaker(this, excludePredicate)

fun MockMaker.filter(includePredicate: (Descriptor) -> Boolean): MockMaker =
    ExcludingMockMaker(this) { descriptor -> includePredicate(descriptor).not() }

fun composeMockMakers(mockMakers: List<MockMaker>): MockMaker = CompositeMockMaker(mockMakers)
fun composeMockMakers(vararg mockMakers: MockMaker): MockMaker = CompositeMockMaker(*mockMakers)

fun createMockMaker(rule: MockingRule, ctx: ExecutionContext): MockMaker = when (rule) {
    MockingRule.LAMBDA -> composeMockMakers(
        AllMockMaker(ctx).filter { descriptor ->
            descriptor.kfgClass(ctx.types)?.getFunctionalInterfaces()?.isNotEmpty() ?: false
        },
        LambdaMockMaker(ctx),
    )

    MockingRule.ANY -> AllMockMaker(ctx)

    MockingRule.UNIMPLEMENTED -> AllMockMaker(ctx).filter { descriptor ->
        val klass = descriptor.kfgClass(ctx.types) ?: return@filter false
        instantiationManager.isInstantiable(klass).not()
    }
}


private const val FUNCTIONAL_INTERFACE_CLASS_NAME = "java/lang/FunctionalInterface"
private val Class.isLambda: Boolean
    get() = isInterface && annotations.any { it.type.name == FUNCTIONAL_INTERFACE_CLASS_NAME }

private fun Class.getFunctionalInterfaces(
    interfaces: MutableSet<Class> = mutableSetOf()
): Set<Class> {
    if (isLambda) {
        interfaces.add(this)
    }
    allAncestors.forEach { it.getFunctionalInterfaces(interfaces) }
    return interfaces
}

private fun Descriptor.kfgClass(types: TypeFactory): Class? =
    (type.getKfgType(types) as? ClassType)?.klass
