package org.jetbrains.research.kex.test.spider.computer.client;

import org.jetbrains.research.kex.test.spider.computer.lib.Computer;

@SuppressWarnings("DuplicatedCode")
public class OtherClass {
    void doSomething() {
        // ok
        Computer computer1 = new Computer();
        computer1.boot();
        computer1.selectOS("win");
        computer1.loadOS();

        // wrong sequence
        Computer computer2 = new Computer();
        computer2.boot();
        computer2.loadOS();
    }
}
