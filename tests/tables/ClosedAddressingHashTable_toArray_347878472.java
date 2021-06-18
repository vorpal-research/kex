package tables;

import java.lang.Throwable;
import java.lang.IllegalStateException;
import org.junit.Test;
import tables.ClosedAddressingHashTable;
import java.lang.Object;

public class ClosedAddressingHashTable_toArray_347878472 {

    public <T> T unknown() {
        throw new IllegalStateException();
    }

    @Test
    public  void testbb1() throws Throwable {
        ClosedAddressingHashTable term16522 = new ClosedAddressingHashTable();
        Object[] term16521 = new Object[0];
        term16522.toArray(term16521);
    }

    @Test
    public  void testbb6() throws Throwable {
        ClosedAddressingHashTable term16542 = new ClosedAddressingHashTable();
        Object term16590 = new Object();
        term16542.add(term16590);
        Object term16558 = new Object();
        term16542.add(term16558);
        Object[] term16536 = new Object[0];
        term16542.toArray(term16536);
    }

};


