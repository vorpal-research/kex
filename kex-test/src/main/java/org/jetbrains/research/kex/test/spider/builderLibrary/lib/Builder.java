package org.jetbrains.research.kex.test.spider.builderLibrary.lib;

public class Builder {
    int field1;
    int field2;
    int field3;

    public Builder set1(int value) {
        field1 = value;
        return this;
    }

    public Builder set2(int value) {
        field2 = value;
        return this;
    }

    public Builder set3(int value) {
        field3 = value;
        return this;
    }

    public Builder returnSetResult(int value) {
        return set2(value);
    }
}
