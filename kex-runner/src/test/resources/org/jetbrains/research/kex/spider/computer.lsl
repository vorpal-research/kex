library Computer;

types {
    Computer (lib.Computer);
    Memory (lib.Memory);
    OS (lib.OS);
    OSName (String);
}

automaton Computer {
    javapackage org.jetbrains.research.kex.test.spider.computer.lib;

    var memory : Memory;

    state Downed;
    state Booted;
    state OSSelected;
    state OSLoaded;
    finishstate Closed;

    shift Downed -> Booted(boot);
    shift Booted -> OSSelected(selectOS);
    shift OSSelected -> OSLoaded(loadOS);
    shift Any -> Closed(shutdown);
}

fun Computer.Computer(): Computer {
    result = new Computer(Downed);
}

fun Computer.boot(){
    memory = new Memory(Close);
}

fun Computer.selectOS(osName: OSName) {
    requires (osName == "win" || osName == "linux"); // memory is Computer's property
}

fun Computer.loadOS();

fun Computer.shutdown();

automaton Memory {
    javapackage org.jetbrains.research.kex.test.spider.computer.lib;
    state Close;
    state Open;

    shift Close -> Open(open);
    shift Open -> self(setOS);
    shift Open -> Close(close);
}

fun Memory.Memory() {
    result = new Memory(Close);
}

fun Memory.open() {
    requires (!(isOpen));
    ensures (isOpen);
}

fun Memory.setOS(os: OS);

fun Memory.getOS(): OS;

fun Memory.close() {
    requires (isOpen);
    ensures (old(!(isOpen)));
}
