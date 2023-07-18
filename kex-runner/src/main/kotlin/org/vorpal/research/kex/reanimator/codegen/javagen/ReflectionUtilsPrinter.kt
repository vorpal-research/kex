package org.vorpal.research.kex.reanimator.codegen.javagen

import org.vorpal.research.kex.asm.util.Visibility
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.util.asmString
import org.vorpal.research.kex.util.getJvmVersion
import org.vorpal.research.kex.util.kapitalize
import org.vorpal.research.kex.util.testcaseDirectory
import java.nio.file.Path

@Suppress("MemberVisibilityCanBePrivate")
class ReflectionUtilsPrinter(
    val packageName: String
) {
    private val builder = JavaBuilder(packageName)
    val klass = builder.run { klass(packageName, REFLECTION_UTILS_CLASS) }
    val newInstance: JavaBuilder.JavaFunction
    val newArray: JavaBuilder.JavaFunction
    val newObjectArray: JavaBuilder.JavaFunction
    val newPrimitiveArrayMap = mutableMapOf<String, JavaBuilder.JavaFunction>()

    val accessField: JavaBuilder.JavaFunction

    val getField: JavaBuilder.JavaFunction
    val setField: JavaBuilder.JavaFunction
    val setPrimitiveFieldMap = mutableMapOf<String, JavaBuilder.JavaFunction>()
    val getPrimitiveFieldMap = mutableMapOf<String, JavaBuilder.JavaFunction>()
    val setElement: JavaBuilder.JavaFunction
    val setPrimitiveElementMap = mutableMapOf<String, JavaBuilder.JavaFunction>()
    val callConstructor: JavaBuilder.JavaFunction
    val callMethod: JavaBuilder.JavaFunction
    val getModifierField: JavaBuilder.JavaFunction

    companion object {
        const val REFLECTION_UTILS_CLASS = "ReflectionUtils"
        private val reflectionUtilsInstances = mutableMapOf<Pair<Path, String>, ReflectionUtilsPrinter>()
        fun reflectionUtils(packageName: String): ReflectionUtilsPrinter {
            val testDirectory = kexConfig.testcaseDirectory
            return reflectionUtilsInstances.getOrPut(testDirectory to packageName) {
                val utils = ReflectionUtilsPrinter(packageName)
                val targetFileName = "ReflectionUtils.java"
                val targetFile = testDirectory
                    .resolve(packageName.asmString)
                    .resolve(targetFileName)
                    .toAbsolutePath()
                    .toFile().also { it.parentFile?.mkdirs() }
                targetFile.writeText(utils.builder.toString())
                utils
            }
        }

        fun reflectionUtilsClasses(): Set<Path> = reflectionUtilsInstances.mapTo(mutableSetOf()) { it.key.first }

        @Suppress("unused")
        fun invalidateAll() {
            reflectionUtilsInstances.clear()
        }
    }

    init {
        with(builder) {
            import("java.lang.Class")
            import("java.lang.reflect.Method")
            import("java.lang.reflect.Constructor")
            import("java.lang.reflect.Field")
            import("java.lang.reflect.Array")
            import("java.lang.reflect.Modifier")
            import("sun.misc.Unsafe")
            import("java.lang.reflect.InvocationTargetException")
            import("org.junit.Ignore")

            with(klass) {
                annotations += "Ignore"
                field("UNSAFE", type("Unsafe")) {
                    visibility = Visibility.PUBLIC
                    modifiers += "static"
                    modifiers += "final"
                }

                static {
                    aTry {
                        +"final Field uns = Unsafe.class.getDeclaredField(\"theUnsafe\")"
                        +"uns.setAccessible(true)"
                        +"UNSAFE = (Unsafe) uns.get(null)"
                    }.catch {
                        exceptions += type("Throwable")
                        +"throw new RuntimeException()"
                    }
                }

                getModifierField = method("getModifiersField") {
                    returnType = type("Field")
                    visibility = Visibility.PUBLIC
                    modifiers += "static"
                    exceptions += "Throwable"

                    when {
                        getJvmVersion() >= 12 -> {
                            +"Field mods = null"
                            +"Method m = Class.class.getDeclaredMethod(\"getDeclaredFields0\", boolean.class)"
                            +"m.setAccessible(true)"
                            +"for (Field f : (Field[]) m.invoke(Field.class, false)) {"
                            +"    if (f.getName().equals(\"modifiers\")) {"
                            +"        mods = f"
                            +"        break"
                            +"    }"
                            +"}"
                            +"return mods"
                        }

                        else -> {
                            +"return Field.class.getDeclaredField(\"modifiers\")"
                        }
                    }
                }

                method("newInstance") {
                    arguments += arg("klass", type("Class<?>"))
                    returnType = type("Object")
                    visibility = Visibility.PUBLIC
                    modifiers += "static"
                    exceptions += "Throwable"

                    +"Object instance = klass.cast(UNSAFE.allocateInstance(klass))"
                    +"return instance"
                }
                val primitiveTypes = listOf("boolean", "byte", "char", "short", "int", "long", "double", "float")

                newInstance = method("newInstance") {
                    arguments += arg("klass", type("String"))
                    returnType = type("Object")
                    visibility = Visibility.PUBLIC
                    modifiers += "static"
                    exceptions += "Throwable"

                    +"Class<?> reflect = Class.forName(klass)"
                    +"return newInstance(reflect)"
                }

                newArray = method("newArray") {
                    arguments += arg("elementType", type("String"))
                    arguments += arg("length", type("int"))
                    returnType = type("Object")
                    visibility = Visibility.PUBLIC
                    modifiers += "static"
                    exceptions += "Throwable"

                    +"Class<?> reflect = Class.forName(elementType)"
                    +"return Array.newInstance(reflect, length)"
                }

                newObjectArray = method("newObjectArray") {
                    arguments += arg("elementType", type("Class<?>"))
                    arguments += arg("length", type("int"))
                    returnType = type("Object")
                    visibility = Visibility.PUBLIC
                    modifiers += "static"
                    exceptions += "Throwable"

                    +"return Array.newInstance(elementType, length)"
                }

                for (type in primitiveTypes) {
                    newPrimitiveArrayMap[type] = method("new${type.kapitalize()}Array") {
                        arguments += arg("length", type("int"))
                        returnType = type("Object")
                        visibility = Visibility.PUBLIC
                        modifiers += "static"
                        exceptions += "Throwable"

                        +"return Array.newInstance(${type}.class, length)"
                    }
                }

                accessField = method("accessField") {
                    arguments += arg("klass", type("Class<?>"))
                    arguments += arg("name", type("String"))
                    returnType = type("Field")
                    visibility = Visibility.PUBLIC
                    modifiers += "static"
                    exceptions += "Throwable"

                    +"Field result = null"
                    +"Class<?> current = klass"
                    aDo {
                        aTry {
                            +"result = current.getDeclaredField(name)"
                        }.catch {
                            exceptions += type("Throwable")
                        }
                        +"current = current.getSuperclass()"
                    }.aWhile("current != null && result == null")
                    anIf("result == null") {
                        +"throw new NoSuchFieldException()"
                    }
                    +"return result"
                }

                getField = method("getField") {
                    arguments += arg("instance", type("Object"))
                    arguments += arg("klass", type("Class<?>"))
                    arguments += arg("name", type("String"))
                    returnType = type("Object")
                    visibility = Visibility.PUBLIC
                    modifiers += "static"
                    exceptions += "Throwable"

                    +"Field field = ${accessField.name}(klass, name)"
                    +"Field mods = ${getModifierField.name}()"
                    +"mods.setAccessible(true)"
                    +"int modifiers = mods.getInt(field)"
                    +"mods.setInt(field, modifiers & ~Modifier.FINAL)"
                    +"field.setAccessible(true)"
                    +"return field.get(instance)"
                }

                setField = method("setField") {
                    arguments += arg("instance", type("Object"))
                    arguments += arg("klass", type("Class<?>"))
                    arguments += arg("name", type("String"))
                    arguments += arg("value", type("Object"))
                    returnType = void
                    visibility = Visibility.PUBLIC
                    modifiers += "static"
                    exceptions += "Throwable"

                    +"Field field = ${accessField.name}(klass, name)"
                    +"Field mods = ${getModifierField.name}()"
                    +"mods.setAccessible(true)"
                    +"int modifiers = mods.getInt(field)"
                    +"mods.setInt(field, modifiers & ~Modifier.FINAL)"
                    +"field.setAccessible(true)"
                    +"field.set(instance, value)"
                }

                for (type in primitiveTypes) {
                    setPrimitiveFieldMap[type] = method("set${type.kapitalize()}Field") {
                        arguments += arg("instance", type("Object"))
                        arguments += arg("klass", type("Class<?>"))
                        arguments += arg("name", type("String"))
                        arguments += arg("value", type(type))
                        returnType = void
                        visibility = Visibility.PUBLIC
                        modifiers += "static"
                        exceptions += "Throwable"

                        +"Field field = ${accessField.name}(klass, name)"
                        +"Field mods = ${getModifierField.name}()"
                        +"mods.setAccessible(true)"
                        +"int modifiers = mods.getInt(field)"
                        +"mods.setInt(field, modifiers & ~Modifier.FINAL)"
                        +"field.setAccessible(true)"
                        +"field.set${type.kapitalize()}(instance, value)"
                    }
                    getPrimitiveFieldMap[type] = method("get${type.kapitalize()}Field") {
                        arguments += arg("instance", type("Object"))
                        arguments += arg("klass", type("Class<?>"))
                        arguments += arg("name", type("String"))
                        returnType = type(type)
                        visibility = Visibility.PUBLIC
                        modifiers += "static"
                        exceptions += "Throwable"

                        +"Field field = ${accessField.name}(klass, name)"
                        +"Field mods = ${getModifierField.name}()"
                        +"mods.setAccessible(true)"
                        +"int modifiers = mods.getInt(field)"
                        +"mods.setInt(field, modifiers & ~Modifier.FINAL)"
                        +"field.setAccessible(true)"
                        +"return field.get${type.kapitalize()}(instance)"
                    }
                }

                setElement = method("setElement") {
                    arguments += arg("array", type("Object"))
                    arguments += arg("index", type("int"))
                    arguments += arg("element", type("Object"))
                    returnType = void
                    visibility = Visibility.PUBLIC
                    modifiers += "static"
                    exceptions += "Throwable"

                    +"Array.set(array, index, element)"
                }
                for (type in primitiveTypes) {
                    setPrimitiveElementMap[type] = method("set${type.kapitalize()}Element") {
                        arguments += arg("array", type("Object"))
                        arguments += arg("index", type("int"))
                        arguments += arg("element", type(type))
                        returnType = void
                        visibility = Visibility.PUBLIC
                        modifiers += "static"
                        exceptions += "Throwable"

                        +"Array.set${type.kapitalize()}(array, index, element)"
                    }
                }

                callConstructor = method("callConstructor") {
                    arguments += arg("klass", type("Class<?>"))
                    arguments += arg("argTypes", type("Class<?>[]"))
                    arguments += arg("args", type("Object[]"))
                    returnType = type("Object")
                    visibility = Visibility.PUBLIC
                    modifiers += "static"
                    exceptions += "Throwable"

                    +"Constructor<?> method = klass.getDeclaredConstructor(argTypes)"
                    +"method.setAccessible(true)"
                    +"return method.newInstance(args)"
                }

                callMethod = method("callMethod") {
                    arguments += arg("klass", type("Class<?>"))
                    arguments += arg("name", type("String"))
                    arguments += arg("argTypes", type("Class<?>[]"))
                    arguments += arg("instance", type("Object"))
                    arguments += arg("args", type("Object[]"))
                    returnType = type("Object")
                    visibility = Visibility.PUBLIC
                    modifiers += "static"
                    exceptions += "Throwable"
                    aTry {
                        +"Method method = klass.getDeclaredMethod(name, argTypes)"
                        +"method.setAccessible(true)"
                        +"return method.invoke(instance, args)"
                    }.catch {
                        exceptions += type("InvocationTargetException")
                        +"throw e.getCause()"
                    }
                }
            }
        }
    }
}
