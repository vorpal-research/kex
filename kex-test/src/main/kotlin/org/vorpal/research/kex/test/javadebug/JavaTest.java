package org.vorpal.research.kex.test.javadebug;

import java.util.HashMap;

@SuppressWarnings("ALL")
public class JavaTest {
    public int foo(HashMap<Integer, Integer> list) {
        if (list.size() == 1) {
            System.out.println("a");
        }
        return 0;
    }
}

