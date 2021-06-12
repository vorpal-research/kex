package tables;

import java.lang.Throwable;
import java.lang.IllegalStateException;
import org.junit.Test;
import tables.ClosedAddressingHashTable;
import java.lang.Object;
import java.lang.String;

public class ClosedAddressingHashTable_remove_1013039074 {

    public <T> T unknown() {
        throw new IllegalStateException();
    }

    @Test
    public  void testentry0() throws Throwable {
        ClosedAddressingHashTable term1865 = new ClosedAddressingHashTable();
        term1865.remove((Object)null);
    }

    @Test
    public  void testbb1() throws Throwable {
        ClosedAddressingHashTable term1878 = new ClosedAddressingHashTable(0);
        Object term1879 = new Object();
        term1878.remove(term1879);
    }

    @Test
    public  void testbb2() throws Throwable {
        ClosedAddressingHashTable term1903 = new ClosedAddressingHashTable(1);
        Object term1904 = new Object();
        term1903.remove(term1904);
    }

    @Test
    public  void testbb4() throws Throwable {
        ClosedAddressingHashTable term1931 = new ClosedAddressingHashTable(770);
        String term1932 = new String();
        term1931.remove(term1932);
    }

    @Test
    public  void testbb5() throws Throwable {
        ClosedAddressingHashTable term1961 = new ClosedAddressingHashTable(770);
        String term1962 = new String();
        term1961.remove(term1962);
    }

    @Test
    public  void testbb6() throws Throwable {
        ClosedAddressingHashTable term1992 = new ClosedAddressingHashTable(33);
        String term2045 = new String();
        term1992.remove(term2045);
        char[] term1989 = new char[1];
        String term1993 = new String((char[])term1989);
        term1992.remove(term1993);
    }

    @Test
    public  void testbb7() throws Throwable {
        ClosedAddressingHashTable term2144 = new ClosedAddressingHashTable(33);
        Object term2192 = new Object();
        term2144.remove(term2192);
        char[] term2141 = new char[1];
        String term2145 = new String((char[])term2141);
        term2144.remove(term2145);
    }

};


