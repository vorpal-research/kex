libsl "1.0.0";
library Computer version "1.0.0";

types {
    Builder(org.jetbrains.research.kex.test.spider.builderLibrary.lib.Builder);
    int(int32);
}

automaton org.jetbrains.research.kex.test.spider.builderLibrary.lib.Builder : Builder {
    initstate Created;
    state Set1, Set2, Set3;

    shift Created -> Set1(set1);
    shift Set1 -> Set2(set2);
    shift Set2 -> Set3(set3);

    fun set1(value: int): Builder;
    fun set2(value: int): Builder;
    fun set3(value: int): Builder;
}
