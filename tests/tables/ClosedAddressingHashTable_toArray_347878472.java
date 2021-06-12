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
        ClosedAddressingHashTable term13361 = new ClosedAddressingHashTable();
        Object[] term13360 = new Object[0];
        term13361.toArray(term13360);
    }

    @Test
    public  void testbb6() throws Throwable {
        ClosedAddressingHashTable term13381 = new ClosedAddressingHashTable(1);
        Object term13438 = new Object();
        term13381.add(term13438);
        Object term13398 = new Object();
        term13381.add(term13398);
        Object[] term13375 = new Object[0];
        term13381.toArray(term13375);
    }

};


