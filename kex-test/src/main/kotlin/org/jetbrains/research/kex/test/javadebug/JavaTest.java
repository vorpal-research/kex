package org.jetbrains.research.kex.test.javadebug;

public abstract class JavaTest {
    protected int value;

    JavaTest(int value) {
        this.value = value;
    }

    static class Impl extends JavaTest {
        public Impl(int a) {
            super(a);
        }

        public void setA(int a) {
            this.value = a;
        }
    }

    public static JavaTest impl() {
        return new Impl(0);
    }

    public static void foo(JavaTest a) {
        if (a instanceof Impl) {
            throw new IllegalStateException();
        }
    }
}