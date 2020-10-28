package org.jetbrains.research.kex.test.javadebug;

public class JavaTest {
    public static int i = 0;

    public void test(int a) {
        if (a + i == 3 && i > 0) {
            System.out.println("a");
        }
    }
}