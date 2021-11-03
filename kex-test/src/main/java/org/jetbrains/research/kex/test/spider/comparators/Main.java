package org.jetbrains.research.kex.test.spider.comparators;

public class Main {
    int foo(Integer i) {
        return i + 1;
    }

    int bar(String str) {
        return str.length();
    }

    void testOk() {
        foo(1);
        bar("test");
    }

    void testNegVal() {
        foo(-1);
    }

    void testEmptyString() {
        bar("");
    }
}
