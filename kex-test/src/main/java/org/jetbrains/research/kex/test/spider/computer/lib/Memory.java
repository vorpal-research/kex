package org.jetbrains.research.kex.test.spider.computer.lib;

public class Memory {
    private OS currentOS = null;
    private boolean isOpen = false;

    public void open() {
        isOpen = true;
    }

    public void setOS(OS os) {
        currentOS = os;
    }

    public void close() {
        isOpen = false;
    }

    public OS getOS() {
        return currentOS;
    }
}