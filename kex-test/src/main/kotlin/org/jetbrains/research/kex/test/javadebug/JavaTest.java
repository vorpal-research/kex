package org.jetbrains.research.kex.test.javadebug;

public class JavaTest {

    private final String str;
    private boolean implicitMultiplication = true;

    public JavaTest(String str) {
        this.str = str;
    }

    public JavaTest implicitMultiplication(boolean enabled) {
        this.implicitMultiplication = enabled;
        return this;
    }

//    public void test(String a) {
//        A b = new A(a);
//        if (b.str.length() > 0) {
//            throw new IllegalArgumentException();
//        }
//    }
}