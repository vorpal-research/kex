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
interface SafeMockMaker {
    fun canMock(descriptor: Descriptor): Boolean
    fun mockOrNull(original: Descriptor): MockDescriptor?
}

// mock is NOT subtype of original
interface BreakingMockMaker {
    fun canMockBreaking(expectedClass: Class, descriptor: Descriptor): Boolean
    fun mockBreakingOrNull(expectedClass: Class, original: Descriptor): MockDescriptor?
}

sealed class AbstractMockMaker(protected val ctx: ExecutionContext) : SafeMockMaker {
    protected val types: TypeFactory = ctx.types
    protected fun satisfiesNecessaryConditions(descriptor: Descriptor): Boolean {
        val klass = getKlass(descriptor) ?: return false
        return !klass.isFinal && !descriptor.type.isKexRt && descriptor is ObjectDescriptor
    }

    protected fun getKlass(descriptor: Descriptor): Class? =
        (descriptor.type.getKfgType(types) as? ClassType)?.klass
}

private class AllMockMaker(ctx: ExecutionContext) : AbstractMockMaker(ctx) {
    override fun canMock(descriptor: Descriptor): Boolean {
        return satisfiesNecessaryConditions(descriptor)
    }

    override fun mockOrNull(original: Descriptor): MockDescriptor? {
        if (!canMock(original)) return null
        original as ObjectDescriptor
        return MockDescriptor(original).also { it.fields.putAll(original.fields) }
    }
}

private class UnimplementedMockMaker(ctx: ExecutionContext) : AbstractMockMaker(ctx) {
    override fun canMock(descriptor: Descriptor): Boolean {
        val klass = (descriptor.type.getKfgType(types) as? ClassType)?.klass ?: return false
        return satisfiesNecessaryConditions(descriptor) &&
                !instantiationManager.isInstantiable(klass)
    }

    override fun mockOrNull(original: Descriptor): MockDescriptor? {
        if (!canMock(original)) return null
        original as ObjectDescriptor
        return MockDescriptor(original).also { it.fields.putAll(original.fields) }
    }
}

private const val FUNCTIONAL_INTERFACE_CLASS_NAME = "java/lang/FunctionalInterface"
private val Class.isLambda: Boolean
    get() = isInterface && annotations.any { it.type.name == FUNCTIONAL_INTERFACE_CLASS_NAME }

private class LambdaMockMaker(ctx: ExecutionContext) : AbstractMockMaker(ctx), BreakingMockMaker {
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
        return getKlass(descriptor)?.isFinal == false && !descriptor.type.isKexRt && descriptor is ObjectDescriptor && functionalInterfaces.isNotEmpty()
    }

    override fun mockOrNull(original: Descriptor): MockDescriptor? {
        if (!canMock(original)) return null
        original as ObjectDescriptor
        val klass = getKlass(original) ?: return null
        if (klass.isLambda) {
            return descriptor { mock(klass.kexType) }
        }
        val mockKlass = (if (klass.isFinal) klass.superClass else klass) ?: return null
        val functionalInterfaces = klass.getFunctionalInterfaces()

        if (functionalInterfaces.isEmpty()) return null
        return descriptor {
            mock(original, mockKlass.kexType, functionalInterfaces.map { it.kexType }.toSet())
        }
    }

    override fun canMockBreaking(expectedClass: Class, descriptor: Descriptor): Boolean {
        return canMock(descriptor) || expectedClass.isLambda
    }

    override fun mockBreakingOrNull(expectedClass: Class, original: Descriptor): MockDescriptor? {
        return when {
            canMock(original) -> mockOrNull(original)
            canMockBreaking(expectedClass, original) ->
                descriptor { mock(original as ObjectDescriptor, expectedClass.kexType) }

            else -> null
        }
    }
}

fun createMockMaker(rule: MockingRule, ctx: ExecutionContext): SafeMockMaker = when (rule) {
    MockingRule.LAMBDA -> LambdaMockMaker(ctx)
    MockingRule.ANY -> AllMockMaker(ctx)
    MockingRule.UNIMPLEMENTED -> UnimplementedMockMaker(ctx)
}
