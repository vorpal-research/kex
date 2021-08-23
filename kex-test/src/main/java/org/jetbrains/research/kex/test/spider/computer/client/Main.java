package org.jetbrains.research.kex.test.spider.computer.client;

import org.jetbrains.research.kex.test.spider.computer.lib.Computer;

public class Main {
    public static void main(String[] args) {
        // ok
        Computer computer1 = new Computer();
        computer1.boot();
        computer1.selectOS("win");
    }
}