package org.jetbrains.research.kex.test.spider.computer.client;

import org.jetbrains.research.kex.test.spider.computer.lib.Computer;

@SuppressWarnings("DuplicatedCode")
public class OtherClass {
    void wrongSequence() {
        Computer computer2 = new Computer();
        computer2.boot();
        computer2.loadOS();
        computer2.selectOS("linux");
    }
}
