package org.jetbrains.research.kex.test.javadebug;

public abstract class JavaTest {
    private int value;

    JavaTest(int value) {
        this.value = value;
    }

    static class Impl extends JavaTest {
        public Impl() {
            super(10);
        }
    }

    static class Impl2 extends JavaTest {
        public Impl2() {
            super(5);
        }
    }

    public static JavaTest impl() {
        return new Impl();
    }

    public static JavaTest impl2() {
        return new Impl2();
    }

    public void foo(int a) {
        if (a > value) {
            throw new IllegalStateException();
        }
    }
}