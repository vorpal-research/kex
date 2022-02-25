package org.jetbrains.research.kex.test.javadebug;

import java.util.LinkedList;

public abstract class JavaTest {
    public static final int N = 4; //some constant value

    public static void sample(LinkedList list, Object obj) {
        list.addLast(obj);
        Object r = list.remove(N);
        if (r == obj) {
            throw new IllegalStateException();
        }
    }

}