package org.vorpal.research.kex.test.concolic;

public enum TestEnum {
    A, B, C;

    int foo() {
        switch (this) {
            case A:
                return 42;
            case B:
                throw new IllegalArgumentException();
            case C:
                return 22;
        }
        throw new IllegalStateException();
    }
}
