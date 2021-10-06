package org.jetbrains.research.kex.test.spider.computer.lib;

public class Computer {
    Memory memory = null;
    boolean isOsLoad = false;
    int partition = 0;

    public void boot() {
        memory = new Memory();
    }

    public void setBootPartition(int partition) {
        if (memory == null) {
            throw new IllegalStateException("memory wasn't initialized");
        }
        if (partition < 0 || partition > 16) {
            throw new IllegalStateException("incorrect partition number");
        }
        this.partition = partition;
    }

    public void selectOS(String osName) {
        if (memory == null) {
            throw new IllegalStateException("memory wasn't initialized");
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