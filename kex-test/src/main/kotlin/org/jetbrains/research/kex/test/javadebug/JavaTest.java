package org.jetbrains.research.kex.test.javadebug;

public abstract class JavaTest {
    private static final JavaTest[] EMPTY_THROWABLE_ARRAY = new JavaTest[0];
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
        if (EMPTY_THROWABLE_ARRAY.length > 0) return EMPTY_THROWABLE_ARRAY[0];
        return new Impl(0);
    }

    public static void foo(Impl a) {
        if (a instanceof JavaTest) {
            throw new IllegalStateException();
        }
    }
}