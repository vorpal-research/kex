libsl "1.0.0";
library Computer version "1.0.0";

types {
    Computer (org.jetbrains.research.kex.test.spider.computer.lib.Computer);
    OS (org.jetbrains.research.kex.test.spider.computer.lib.OS);
    OSName (string);
}

automaton org.jetbrains.research.kex.test.spider.computer.lib.Computer : Computer {
    initstate Downed;
    state Booted;
    state OSSelected;
    state OSLoaded;
    finishstate Closed;

    var isMemoryInit: bool = false;
    var isOsLoaded: bool = false;

    shift Downed -> Booted(boot);
    shift Booted -> OSSelected(selectOS);
    shift OSSelected -> OSLoaded(loadOS);
    shift any -> Closed(shutdown);

    fun boot()
        requires isMemoryNotInit: !isMemoryInit;
    {
        isMemoryInit = true;
    }

    fun selectOS(osName: OSName)
        requires osNameCheck: (osName = "win" | osName = "linux");

    fun loadOS()
        requires isMemoryInit: isMemoryInit;
        requires isOsNotLoaded: !isOsLoaded;
    {
        isOsLoaded = true;
    }

    fun shutdown() {
        isOsLoaded = false;
        isMemoryInit = false;
    }
}
