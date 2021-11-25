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

    public void foo(int a) {
        if (value > 1 && a > value) {
            throw new IllegalStateException();
        }
    }
}