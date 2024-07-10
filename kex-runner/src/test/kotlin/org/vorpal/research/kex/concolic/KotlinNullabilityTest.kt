package org.vorpal.research.kex.concolic

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import org.junit.Test
import org.vorpal.research.kex.KexRunnerTest
import org.vorpal.research.kex.asm.analysis.concolic.InstructionConcolicChecker
import org.vorpal.research.kex.asm.manager.ClassInstantiationDetector
import org.vorpal.research.kex.config.RuntimeConfig
import org.vorpal.research.kex.descriptor.ArrayDescriptor
import org.vorpal.research.kex.descriptor.ConstantDescriptor
import org.vorpal.research.kex.descriptor.Descriptor
import org.vorpal.research.kex.descriptor.ObjectDescriptor
import org.vorpal.research.kex.parameters.Parameters
import org.vorpal.research.kex.trace.runner.ExecutorMasterController
import org.vorpal.research.kfg.Package
import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.visitor.executePackagePipeline
import org.vorpal.research.kthelper.logging.log
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

@ExperimentalTime
@ExperimentalSerializationApi
@InternalSerializationApi
@DelicateCoroutinesApi
class KotlinNullabilityTest : KexRunnerTest("kotlin-nullability") {
    private val prefix = "org/vorpal/research/kex/test/concolic/kotlinNullability/"

    init {
        RuntimeConfig.setValue("test", "collectArgumentFromTraces", true)
        RuntimeConfig.setValue("kex", "useReflectionInfo", true)
    }

    private fun runAndCollectArguments(klass: Class): Map<Method, List<Parameters<Descriptor>>> {
        val collectedArguments = mutableMapOf<Method, List<Parameters<Descriptor>>>()
        repeat(N_RUNS) {
            ExecutorMasterController.use {
                it.start(analysisContext)
                executePackagePipeline(analysisContext.cm, Package.defaultPackage) {
                    +ClassInstantiationDetector(analysisContext)
                }
                for (method in klass.allMethods.sortedBy { method -> method.prototype }) {
                    val collectedFromRun = InstructionConcolicChecker.run(analysisContext, setOf(method))
                    collectedArguments.putAll(collectedFromRun)
                }
                log.debug("collected arguments: {}", collectedArguments)
            }
        }
        return collectedArguments.filterKeys { !it.isConstructor }
    }

    @Test
    fun nonNullablePrimitives() {
        val collectedArguments = runAndCollectArguments(cm[prefix + "WithoutNullableTypesTest"])
        assertTrue {
            collectedArguments
                .filter { (method) -> method.name == "withoutNullableTypes" }
                .values
                .first()
                .all { it.arguments.all { it !is ConstantDescriptor.Null } }
        }
    }

    @Test
    fun nonNullableArraysWithNonNullableElements() {
        val collectedArguments = runAndCollectArguments(cm[prefix + "WithArrayWithoutNullsTest"])
        assertTrue {
            collectedArguments
                .filter { (method) -> method.name == "withArrayWithoutNulls" }
                .values
                .first()
                .all {
                    it
                        .arguments
                        .all { it is ArrayDescriptor && it.elements.values.all { it !is ConstantDescriptor.Null } }
                }
        }
    }

    @Test
    fun nonNullableArraysWithNullableElements() {
        val collectedArguments = runAndCollectArguments(cm[prefix + "WithArrayWithNullsTest"])
        assertTrue {
            val args = collectedArguments
                .filter { (method) -> method.name == "withArrayWithNulls" }
                .values
                .first()
            if (!args.all { it.arguments.all { it is ArrayDescriptor } })
                return@assertTrue false
            val filtered = args.map { it.arguments.filterIsInstance<ArrayDescriptor>().first() }
            filtered.any { it.elements.values.all { it is ConstantDescriptor.Null } } &&
                    filtered.any { it.elements.values.any { it !is ConstantDescriptor.Null } }
        }
    }

    @Test
    fun nullableArrayWithNonNullableElements() {
        val collectedArguments = runAndCollectArguments(cm[prefix + "WithNullableArrayTest"])
        assertTrue {
            val args = collectedArguments
                .filter { (method) -> method.name == "withNullableArray" }
                .values
                .first()
            args.any { it.arguments.all { it is ConstantDescriptor.Null } }
                    && args.any { it.arguments.all { it is ArrayDescriptor && it.elements.values.all { it !is ConstantDescriptor.Null } } }
        }
    }

    @Test
    fun nullableArrayWithNullableElements() {
        val collectedArguments = runAndCollectArguments(cm[prefix + "WithNullableArrayWithNullableTypes"])
        assertTrue {
            val args = collectedArguments
                .filter { (method) -> method.name == "withNullableArrayWithNullableTypes" }
                .values
                .first()
            args.any { it.arguments.all { it is ConstantDescriptor.Null } } &&
                    args.any { it.arguments.all { it is ArrayDescriptor && it.elements.values.all { it is ConstantDescriptor.Null } } } &&
                    args.any { it.arguments.all { it is ArrayDescriptor && it.elements.values.all { it !is ConstantDescriptor.Null } } }
        }
    }

    private fun <T> foldListTerm(list: ObjectDescriptor, initialValue: T, itemVisitor: (Descriptor, T) -> T): T {
        require(list.klass.klass == "java/util/LinkedList")
        val (firstName, nodeType) = list.fields.keys.find { (name) -> name == "first" }!!
        var node: Descriptor = list.fields[firstName to nodeType]!!
        var accumulate: T = initialValue
        while (node is ObjectDescriptor) {
            val itemKey = node.fields.keys.find { (name) -> name == "item" }!!
            val item = node.fields[itemKey]!!
            accumulate = itemVisitor(item, accumulate)
            val nextKey = node.fields.keys.find { (name) -> name == "next" }!!
            val next = node.fields[nextKey]!!
            node = next
        }
        return accumulate
    }

    private fun <T> foldKexList(list: ObjectDescriptor, initialValue: T, itemVisitor: (Descriptor, T) -> T): T {
        require(list.klass.klass == "kex/java/util/LinkedList")
        val innerField = list.fields.keys.find { (name) -> name == "inner" } ?: return initialValue
        val inner = list.fields[innerField]!!
        require(inner is ObjectDescriptor)
        val elementDataKey = inner.fields.keys.find { (name) -> name == "elementData" } ?: return initialValue
        val elementData = inner.fields[elementDataKey]!!
        require(elementData is ArrayDescriptor)
        return (0 until elementData.length)
            .fold(initialValue) { acc, idx -> itemVisitor(elementData.elements[idx]!!, acc) }
    }

    private fun traverseList(list: ObjectDescriptor, visitor: (Descriptor) -> Unit) {
        val wrapper = { desc: Descriptor, _: Unit -> visitor(desc) }
        when (list.klass.klass) {
            "java/util/LinkedList" -> foldListTerm(list, Unit, wrapper)
            "kex/java/util/LinkedList" -> foldKexList(list, Unit, wrapper)
            else -> error("${list.klass.klass} is not a list class")
        }
    }

    private val Descriptor.isList: Boolean
        get() = this is ObjectDescriptor
                && (this.klass.klass == "java/util/LinkedList" || this.klass.klass == "kex/java/util/LinkedList")
    private val Descriptor.isNullableList: Boolean
        get() = this.isList || this is ConstantDescriptor.Null

    @Test
    fun nonNullableListWithNonNullableElements() {
        val collectedArguments = runAndCollectArguments(cm[prefix + "WithListWithoutNullsTest"])
        val args = collectedArguments
            .filter { (method) -> method.name == "withListWithoutNulls" }
            .values
            .first()
        assertTrue { args.all { it.arguments.all { it.isList } && it.arguments.size == 1 } }
        args
            .map { it.arguments.filterIsInstance<ObjectDescriptor>().first() }
            .forEach {
                traverseList(it) { item: Descriptor ->
                    assertTrue(item is ObjectDescriptor)
                    val itemValueKey = item.fields.keys.find { (name) -> name == "value" }!!
                    val itemValue = item[itemValueKey]!!
                    assertTrue(itemValue !is ConstantDescriptor.Null)
                    print("(item $itemValue) ")
                }
                println()
            }
    }


    @Test
    fun nonNullableListWithNullElements() {
        val collectedArguments = runAndCollectArguments(cm[prefix + "WithListWithNullsTest"])
        val args = collectedArguments
            .filter { (method) -> method.name == "withListWithNulls" }
            .values
            .first()
        assertTrue { args.all { it.arguments.all { it.isList } && it.arguments.size == 1 } }
        var nullCount = 0
        var notNullCount = 0
        args
            .map { it.arguments.filterIsInstance<ObjectDescriptor>().first() }
            .forEach {
                traverseList(it) { item: Descriptor ->
                    if (item is ConstantDescriptor.Null) {
                        nullCount++
                        return@traverseList
                    }
                    assertTrue(item is ObjectDescriptor)
                    if (item.fields.isEmpty()) {
                        nullCount++
                        return@traverseList
                    }
                    val itemValueKey = item.fields.keys.find { (name) -> name == "value" }!!
                    val itemValue = item[itemValueKey]!!
                    if (itemValue is ConstantDescriptor.Null) {
                        nullCount++
                    } else {
                        notNullCount++
                    }
                    print("(item $itemValue) ")
                }
                println()
            }
        assertTrue("There is no null element generated") { nullCount > 0 }
        assertTrue("There is no not null element generated") { notNullCount > 0 }
    }

    @Test
    fun nullableListWithNullableElements() {
        val collectedArguments = runAndCollectArguments(cm[prefix + "WithNullableListWithNullsTest"])
        val args = collectedArguments
            .filter { (method) -> method.name == "withNullableListWithNulls" }
            .values
            .first()
        assertTrue { args.all { it.arguments.all { it.isNullableList } && it.arguments.size == 1 } }
        assertTrue { args.any { it.arguments.first().isList } && args.any { it.arguments.first().isNullableList } }
        var nullCount = 0
        var notNullCount = 0
        args
            .map { it.arguments.first() }
            .filterIsInstance<ObjectDescriptor>()
            .forEach {
                traverseList(it) { item: Descriptor ->
                    if (item is ConstantDescriptor.Null) {
                        nullCount++
                        return@traverseList
                    }
                    assertTrue(item is ObjectDescriptor)
                    val itemValueKey = item.fields.keys.find { (name) -> name == "value" }!!
                    val itemValue = item[itemValueKey]!!
                    if (itemValue is ConstantDescriptor.Null) {
                        nullCount++
                    } else {
                        notNullCount++
                    }
                    print("(item $itemValue) ")
                }
                println()
            }
        assertTrue("There is no null element generated") { nullCount > 0 }
        assertTrue("There is no not null element generated") { notNullCount > 0 }
    }


    companion object {
        private const val N_RUNS = 1
    }
}