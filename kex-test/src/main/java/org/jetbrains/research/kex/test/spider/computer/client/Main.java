package org.jetbrains.research.kex.test.spider.computer.client;

import org.jetbrains.research.kex.test.spider.computer.lib.Computer;

public class Main {
    void testOk() {
        Computer computer1 = new Computer();
        computer1.boot();
        computer1.setBootPartition(1);
        computer1.selectOS("win");
        computer1.loadOS();
    }

    void testWrongSequence() {
        Computer computer2 = new Computer();
        computer2.boot();
        computer2.loadOS();
        computer2.selectOS("linux");
    }

    void testFromFinishstate() {
        Computer computer3 = new Computer();
        computer3.shutdown(); // finishstate!
        computer3.boot();
    }

    void testPreconditionViolation1() {
        // precondition violation: partition must be non-negative
        Computer computer4 = new Computer();
        computer4.boot();
        computer4.setBootPartition(-1);
    }

    void testPreconditionViolation2() {
        // precondition violation: unexpected osName
        Computer computer5 = new Computer();
        computer5.boot();
        computer5.setBootPartition(1);
        computer5.selectOS("freebsd");
    }
}