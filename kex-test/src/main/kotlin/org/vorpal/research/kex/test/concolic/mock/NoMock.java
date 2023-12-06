package org.vorpal.research.kex.test.concolic.mock;

class NoMock {
    int field;

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof NoMock)) {
            return false;
        }
        return ((NoMock) obj).field == this.field;
    }

    int foo() {
        return field;
    }
}
