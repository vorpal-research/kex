package org.jetbrains.research.kex.test.javadebug;

import java.util.List;

public class JavaTest {

    public void test(List<Integer> list) {
        if (list.size() > 3) {
            System.out.println("aaa");
        }
        System.out.println("bbb");
    }
}
