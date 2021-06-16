package tables;

import java.util.*;

public class ClosedAddressingHashTable<T> implements Set<T> {

    public int capacity;

    private final float loadFactor;

    private LinkedList<T>[] storage;

    private int size = 0;

    private int hash(Object element) {
        Objects.requireNonNull(element);
        int code = element.hashCode();
        if (code >= 0) return code % capacity;
        else return (capacity - 1) - (Math.abs(code) % capacity);
    }

    @SuppressWarnings("unchecked")
    public ClosedAddressingHashTable(int capacity, float loadFactor) {
        this.capacity = capacity;
        this.loadFactor = loadFactor;
        this.storage = new LinkedList[capacity];
    }

    public ClosedAddressingHashTable(int capacity) {
        this(capacity, 0.75f);
    }

    public ClosedAddressingHashTable(float loadFactor) {
        this(16, loadFactor);
    }

    public ClosedAddressingHashTable() {
        this(16, 0.75f);
    }

    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    public boolean contains(Object o) {
        if (o == null) return false;
        LinkedList<T> current = storage[hash(o)];
        return current != null && current.contains(o);
    }

    public boolean add(T t) {
        if (size >= (int) (capacity * loadFactor)) resize();
        int index = hash(t);
        LinkedList<T> current = storage[index];
        if (current == null) {
            storage[index] = new LinkedList<>();
            storage[index].add(t);
        } else if (!current.contains(t)) storage[index].add(t);
        else return false;
        size++;
        return true;
    }

    @SuppressWarnings("unchecked")
    private void resize() {
        capacity *= 2;
        if (capacity <= 0) throw new IllegalStateException("Table capacity can't be more than max value of integer");
        LinkedList<T>[] oldStorage = storage;
        storage = new LinkedList[capacity];
        for (LinkedList<T> list : oldStorage) {
            if (list != null) {
                for (T t : list) {
                    int index = hash(t);
                    if (storage[index] == null) storage[index] = new LinkedList<>();
                    storage[index].add(t);
                }
            }
        }
    }

    public boolean remove(Object o) {
        if (o == null) return false;
        int index = hash(o);
        if (storage[index] != null && storage[index].remove(o)) {
            size--;
            return true;
        } else return false;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        Objects.requireNonNull(c);
        if (c.isEmpty()) return false;
        for (Object o : c)
            if (!contains(o)) return false;
        return true;
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        Objects.requireNonNull(c);
        boolean changed = false;
        if (!c.isEmpty()) {
            for (T t : c)
                if (t != null) changed = add(t);
        }
        return changed;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        Objects.requireNonNull(c);
        if (c.isEmpty()) return false;
        boolean changed = false;
        if (size > c.size()) {
            for (Object o : c) changed = remove(o);
        } else changed = removeIf(c::contains); //uses iterator()
        return changed;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        Objects.requireNonNull(c);
        if (c.isEmpty()) return false;
        boolean changed = false;
        Iterator<T> iter = iterator();
        while (iter.hasNext())
            if (!c.contains(iter.next())) {
                iter.remove();
                changed = true;
            }
        return changed;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void clear() {
        storage = new LinkedList[capacity];
        size = 0;
    }

    @Override
    public Object[] toArray() {
        Object[] array = new Object[size];
        int index = 0;
        Iterator<T> iter = iterator();
        while (iter.hasNext()) {
            array[index] = iter.next();
            index++;
        }
        return array;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T1> T1[] toArray(T1[] a) {
        if (a.length < size) return (T1[]) Arrays.copyOf(toArray(), size, a.getClass());
        System.arraycopy(toArray(), 0, a, 0, size);
        return a;
    }

    @Override
    public Iterator<T> iterator() {
        return new ClosedAddressingHashTableIterator();
    }

    private class ClosedAddressingHashTableIterator implements Iterator<T> {

        private int iterations = 0;

        private T current = null;

        private int index = -1;

        private Iterator<T> iter = null;

        @Override
        public boolean hasNext() {
            return iterations < size;
        }

        @Override
        public T next() {
            if (hasNext()) {
                if (iter == null || !iter.hasNext()) {
                    index++;
                    while (storage[index] == null || storage[index].isEmpty()) {
                        index++;
                    }
                    iter = storage[index].iterator();
                }
                current = iter.next();
                iterations++;
                return current;
            } else throw new NoSuchElementException();
        }

        @Override
        public void remove() {
            if (current != null) {
                iter.remove();
                current = null;
                size--;
                iterations--;
            } else throw new IllegalStateException();
        }
    }
}
