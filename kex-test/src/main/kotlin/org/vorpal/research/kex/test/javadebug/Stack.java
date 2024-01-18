package org.vorpal.research.kex.test.javadebug;

@SuppressWarnings("ALL")
public class Stack {

    private Stack() {
    }

    public static void foo(int x, int y) {
        if (x > 0 && bar(x, y) == 0) {
            throw new RuntimeException("kek");
        }
    }

    private static int bar(int x, int y) {
        return x + y * y - x * y;
    }

}
