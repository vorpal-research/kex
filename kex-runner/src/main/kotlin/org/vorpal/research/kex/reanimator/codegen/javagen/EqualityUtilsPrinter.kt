package org.vorpal.research.kex.reanimator.codegen.javagen

import org.vorpal.research.kex.asm.util.Visibility
import org.vorpal.research.kex.config.kexConfig
import org.vorpal.research.kex.util.asmString
import org.vorpal.research.kex.util.getJvmVersion
import org.vorpal.research.kex.util.kapitalize
import org.vorpal.research.kex.util.testcaseDirectory
import java.nio.file.Path

class EqualityUtilsPrinter(
    val packageName: String
) {
    private val builder = JavaBuilder(packageName)
    val klass = builder.run { klass(packageName, EQUALITY_UTILS_CLASS) }
    val equalsPrimitiveMap = mutableMapOf<String, JavaBuilder.JavaFunction>()
    val equalsPrimitiveArrayMap = mutableMapOf<String, JavaBuilder.JavaFunction>()
    val equalsObject: JavaBuilder.JavaFunction
    val equalsAll: JavaBuilder.JavaFunction
    val getAllFields: JavaBuilder.JavaFunction
    val customEquals: JavaBuilder.JavaFunction

    companion object {
        const val EQUALITY_UTILS_CLASS = "EqualityUtils"
        private val equalityUtilsInstances = mutableMapOf<Pair<Path, String>, EqualityUtilsPrinter>()
        fun equalityUtils(packageName: String): EqualityUtilsPrinter {
            val testDirectory = kexConfig.testcaseDirectory
            return equalityUtilsInstances.getOrPut(testDirectory to packageName) {
                val utils = EqualityUtilsPrinter(packageName)
                val targetFileName = "EqualityUtils.java"
                val targetFile = testDirectory
                    .resolve(packageName.asmString)
                    .resolve(targetFileName)
                    .toAbsolutePath()
                    .toFile().also { it.parentFile?.mkdirs() }
                targetFile.writeText(utils.builder.toString())
                utils
            }
        }

        @Suppress("unused")
        fun invalidateAll() {
            equalityUtilsInstances.clear()
        }
    }

    init {
        with(builder) {
            import("java.util.*;")
            import("java.lang.reflect.Field")

            with(klass) {
                val primitiveTypes = listOf("boolean", "byte", "char", "short", "int", "long", "double", "float")
                val primitiveTypesAsObjects = listOf("Boolean", "Byte", "Character", "Short", "Integer", "Long", "Double", "Float")

                for (type in primitiveTypes) {
                    equalsPrimitiveMap[type] = method("equals${type.kapitalize()}") {
                        arguments += arg("t1", type(type))
                        arguments += arg("t2", type(type))
                        returnType = type("boolean")
                        visibility = Visibility.PUBLIC
                        modifiers += "static"
                        +"return t1 == t2"
                    }
                }

                for (type in primitiveTypes) {
                    equalsPrimitiveArrayMap[type] = method("equals${type.kapitalize()}Array") {
                        arguments += arg("t1", type("$type[]"))
                        arguments += arg("t2", type("$type[]"))
                        returnType = type("boolean")
                        visibility = Visibility.PUBLIC
                        modifiers += "static"
                        +"if (t1.length != t2.length) return false"
                        aFor("int i=0; i<t1.length; i++") {
                            +"if (t1[i] != t2[i]) return false"
                        }
                        +"return true"
                    }
                }

                equalsObject = method("equalsObject") {
                    arguments += arg("t1", type("Object"))
                    arguments += arg("t2", type("Object"))
                    arguments += arg("visitedFirstToSecond", type("Map<Object, Object>"))
                    arguments += arg("visitedSecondToFirst", type("Map<Object, Object>"))
                    returnType = type("boolean")
                    visibility = Visibility.PUBLIC
                    modifiers += "static"
                    anIf("visitedFirstToSecond.containsKey(t1) || visitedSecondToFirst.containsKey(t2)") {
                        +"return visitedFirstToSecond.get(t1) == t2 && visitedSecondToFirst.get(t2) == t1"
                    }
                    +"visitedFirstToSecond.put(t1, t2)"
                    +"visitedSecondToFirst.put(t2, t1)"
                    +"Map<String, Object> t1Fields = getAllFields(t1.getClass(), t1)"
                    +"Map<String, Object> t2Fields = getAllFields(t2.getClass(), t2)"
                    aFor("Map.Entry<String, Object> entry : t1Fields.entrySet()") {
                        +"if (!t2Fields.containsKey(entry.getKey())) return false"
                        +"if (!equalsAll(entry.getValue(), t2Fields.get(entry.getKey()), visitedFirstToSecond, visitedSecondToFirst)) return false"
                    }
                    +"visitedFirstToSecond.remove(t1)"
                    +"visitedSecondToFirst.remove(t2)"
                    +"return true"
                }

                equalsAll = method("equalsAll") {
                    arguments += arg("t1", type("Object"))
                    arguments += arg("t2", type("Object"))
                    arguments += arg("visitedFirstToSecond", type("Map<Object, Object>"))
                    arguments += arg("visitedSecondToFirst", type("Map<Object, Object>"))
                    returnType = type("boolean")
                    visibility = Visibility.PUBLIC
                    modifiers += "static"
                    +"if (t1 == null && t2 == null) return true"
                    +"if (t1 == null || t2 == null) return false"
                    +"if (t1.getClass() != t2.getClass()) return false"
                    +"Class clazz = t1.getClass()"

                    for ((i, typeAsObject) in primitiveTypesAsObjects.withIndex()) {
                        val type = primitiveTypes[i]
                        anIf("clazz == $typeAsObject.class") {
                            +"return equals${type.kapitalize()}(($typeAsObject) t1, ($typeAsObject) t2)"
                        }
                    }

                    for (type in primitiveTypes) {
                        anIf("clazz == $type[].class") {
                            +"return equals${type.kapitalize()}Array(($type[]) t1, ($type[]) t2)"
                        }
                    }

                    +"return equalsObject(t1, t2, visitedFirstToSecond, visitedSecondToFirst)"
                }

                getAllFields = method("getAllFields") {
                    arguments += arg("clazz", type("Class"))
                    arguments += arg("obj", type("Object"))
                    returnType = type("Map<String, Object>")
                    visibility = Visibility.PUBLIC
                    modifiers += "static"
                    anIf("clazz == null") {
                        +"return Collections.emptyMap()"
                    }
                    +"Map<String, Object> result = new HashMap<>(getAllFields(clazz.getSuperclass(), obj))"
                    +"Field[] fields = clazz.getDeclaredFields()"
                    aFor("Field field : fields") {
                        +"field.setAccessible(true)"
                        aTry {
                            +"result.put(field.getName(), field.get(obj))"
                        }.catch {
                            exceptions += type("IllegalAccessException")
                        }
                    }
                    +"return result"
                }

                customEquals = method("customEquals") {
                    arguments += arg("t1", type("Object"))
                    arguments += arg("t2", type("Object"))
                    returnType = type("boolean")
                    visibility = Visibility.PUBLIC
                    modifiers += "static"
                    +"return equalsAll(t1, t2, new HashMap<>(), new HashMap<>())"
                }
            }
        }
    }
}
