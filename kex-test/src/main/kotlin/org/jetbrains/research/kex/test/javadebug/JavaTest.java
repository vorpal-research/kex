package org.jetbrains.research.kex.test.javadebug;

public class JavaTest {

    public void test(String a, String b) {
        if (a.startsWith("a32")) {
            if (b.contains(a)) {
                throw new IllegalArgumentException();
            }
        }
    }
}