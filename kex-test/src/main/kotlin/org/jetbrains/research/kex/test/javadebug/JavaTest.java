package org.jetbrains.research.kex.test.javadebug;

public abstract class JavaTest {

    public static void test(Class<?> a) {
        if (a != null) {
            System.out.println(a.getName());
        }
    }
}