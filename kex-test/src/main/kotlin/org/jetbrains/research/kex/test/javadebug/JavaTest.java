package org.jetbrains.research.kex.test.javadebug;

public abstract class JavaTest {

    enum A {A, B, C}

    public static void test(A a) {
        if (a != A.B) {
            System.out.println("foo");
        }
    }
}