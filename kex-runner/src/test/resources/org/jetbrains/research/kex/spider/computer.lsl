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

    fun boot() {
        isMemoryInit = true;
    }

    fun selectOS(osName: OSName);

    fun loadOS(){
        isOsLoaded = true;
    }

    fun shutdown() {
        isOsLoaded = false;
        isMemoryInit = false;
    }
}
