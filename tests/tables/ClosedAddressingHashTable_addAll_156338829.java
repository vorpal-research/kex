package tables;

import java.lang.Throwable;
import java.lang.IllegalStateException;
import org.junit.Test;
import tables.ClosedAddressingHashTable;
import java.lang.Object;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.lang.Float;
import java.lang.String;

public class ClosedAddressingHashTable_addAll_156338829 {

    public <T> T unknown() {
        throw new IllegalStateException();
    }

    @Test
    public  void testlabel0() throws Throwable {
        ClosedAddressingHashTable term7740 = new ClosedAddressingHashTable();
        ConcurrentLinkedQueue<Object> term7741 = new ConcurrentLinkedQueue<Object>();
        term7740.addAll(term7741);
    }

    @Test
    public  void testlabelderoll0() throws Throwable {
        ClosedAddressingHashTable term7759 = new ClosedAddressingHashTable();
        ArrayList term7760 = unknown();
        term7759.addAll((Collection<Object>)term7760);
    }

    @Test
    public  void testbbderoll0() throws Throwable {
        ClosedAddressingHashTable term8571 = new ClosedAddressingHashTable();
        LinkedList<Object> term8572 = new LinkedList<Object>();
        term8571.addAll(term8572);
    }

    @Test
    public  void testifelsederoll0() throws Throwable {
        ClosedAddressingHashTable term8590 = new ClosedAddressingHashTable();
        ArrayList term8591 = unknown();
        term8590.addAll((Collection<Object>)term8591);
    }

    @Test
    public  void testlabelderoll1() throws Throwable {
        ClosedAddressingHashTable term9401 = new ClosedAddressingHashTable();
        ArrayList term9402 = unknown();
        term9401.addAll((Collection<Object>)term9402);
    }

    @Test
    public  void testlabelderoll2() throws Throwable {
        ClosedAddressingHashTable term10211 = new ClosedAddressingHashTable(1);
        Object term10264 = new Object();
        term10211.remove(term10264);
        ArrayList term10212 = unknown();
        term10211.addAll((Collection<Object>)term10212);
    }

    @Test
    public  void testbbderoll2() throws Throwable {
        ClosedAddressingHashTable term11166 = new ClosedAddressingHashTable();
        ClosedAddressingHashTable term11214 = new ClosedAddressingHashTable();
        term11166.remove(term11214);
        LinkedList<Object> term11167 = new LinkedList<Object>();
        term11166.addAll(term11167);
    }

    @Test
    public  void testifelsederoll1() throws Throwable {
        ClosedAddressingHashTable term11270 = new ClosedAddressingHashTable(257, Float.POSITIVE_INFINITY);
        ArrayList term11271 = unknown();
        term11270.addAll((Collection<Object>)term11271);
    }

    @Test
    public  void testbbderoll1() throws Throwable {
        ClosedAddressingHashTable term12110 = new ClosedAddressingHashTable(832, Float.POSITIVE_INFINITY);
        String term12165 = new String();
        term12110.remove(term12165);
        term12110.capacity = -406978544;
        LinkedList<Object> term12111 = new LinkedList<Object>();
        term12110.addAll(term12111);
    }

};


