package org.jetbrains.research.kex.test.spider.builderLibrary.client;

import org.jetbrains.research.kex.test.spider.builderLibrary.lib.*;

public class Main {
    public static void main(String[] args) {
        // analysis on this case works bad because of wrong builder pattern support.
        // KFG builder can't link object with method's return result
        // todo: fix this testcase
        Builder builder1 = new Builder();
        builder1.set1(1)
                .set2(2)
                .set3(3);

        Builder builder2 = new Builder();
        builder2.set1(1)
                // error here, missing set2
                .set3(3);

        Builder builder3 = new Builder();
        builder3.set1(1);
        builder3.set2(2);
        builder3.set3(3);

        Builder builder4 = new Builder();
        builder4.set1(1);
        // error here
        builder4.set3(3);
    }
}
