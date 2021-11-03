libsl "1.0.0";
library comps version "1.0" url "";

types {
    String (string);
    Integer (java.lang.Integer);
    Main (org.jetbrains.research.kex.test.spider.comparators.Main);
}

automaton org.jetbrains.research.kex.test.spider.comparators.Main : Main {
    initstate Created;

    fun foo(i: Integer): Integer
        requires iPos: i > 0;

    fun bar(str: String): Integer
        requires strNotEmpty: str != "";
}
