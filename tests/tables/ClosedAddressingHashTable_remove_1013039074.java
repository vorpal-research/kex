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
        ClosedAddressingHashTable term4059 = new ClosedAddressingHashTable();
        term4059.remove((Object)null);
    }

    @Test
    public  void testbb1() throws Throwable {
        ClosedAddressingHashTable term4072 = new ClosedAddressingHashTable(0);
        Object term4073 = new Object();
        term4072.remove(term4073);
    }

    @Test
    public  void testbb2() throws Throwable {
        ClosedAddressingHashTable term4097 = new ClosedAddressingHashTable(1);
        Object term4098 = new Object();
        term4097.remove(term4098);
    }

    @Test
    public  void testbb4() throws Throwable {
        ClosedAddressingHashTable term4125 = new ClosedAddressingHashTable(770);
        String term4126 = new String();
        term4125.remove(term4126);
    }

    @Test
    public  void testbb5() throws Throwable {
        ClosedAddressingHashTable term4155 = new ClosedAddressingHashTable(770);
        String term4156 = new String();
        term4155.remove(term4156);
    }

    @Test
    public  void testbb6() throws Throwable {
        ClosedAddressingHashTable term4186 = new ClosedAddressingHashTable(33);
        String term4240 = new String();
        term4186.remove(term4240);
        char[] term4183 = new char[1];
        String term4187 = new String((char[])term4183);
        term4186.remove(term4187);
    }

    @Test
    public  void testbb7() throws Throwable {
        ClosedAddressingHashTable term4336 = new ClosedAddressingHashTable(2);
        Object term4383 = new Object();
        term4336.remove(term4383);
        String term4337 = new String();
        term4336.remove(term4337);
    }

};


