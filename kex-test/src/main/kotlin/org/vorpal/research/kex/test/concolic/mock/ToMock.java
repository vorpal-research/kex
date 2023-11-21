package org.vorpal.research.kex.test.concolic.mock;

public interface ToMock {
    int foo();

    String bar();

    ToMock recursion();
}
