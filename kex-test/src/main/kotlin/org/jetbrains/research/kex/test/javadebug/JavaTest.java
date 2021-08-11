package org.jetbrains.research.kex.test.javadebug;

public class JavaTest {

    public void test(String a) {
        if (a.charAt(3) == 'a') {
            throw new IllegalArgumentException();
        }
    }
}