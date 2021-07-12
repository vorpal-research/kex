package org.jetbrains.research.kex.test.spider.computer.lib;

public class Computer {
    Memory memory = null;
    boolean isOsLoad = false;

    public void boot() {
        memory = new Memory();
    }

    public void selectOS(String osName) {
        if (memory == null) {
            throw new IllegalStateException("Os wasn't loaded");
        }
        if (osName.equals("win")) {
            memory.setOS(OS.WIN);
        } else if (osName.equals("linux")) {
            memory.setOS(OS.LINUX);
        } else {
            throw new IllegalArgumentException("Invalid OS name");
        }
    }

    public void loadOS() {
        isOsLoad = true;
    }

    public void shutdown() {
        memory = null;
        isOsLoad = false;
    }
}