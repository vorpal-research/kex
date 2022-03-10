package org.jetbrains.research.kex.test.javadebug;

import java.util.ArrayList;

public abstract class JavaTest {

    public static void test(ArrayList<String> a) {
        if (a.contains("asd")) {
            System.out.println(a.get(0));
        }
    }
}