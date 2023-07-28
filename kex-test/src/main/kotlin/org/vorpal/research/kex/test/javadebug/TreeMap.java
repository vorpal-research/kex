package org.vorpal.research.kex.test.javadebug;

import java.util.NoSuchElementException;

class Range {
    final int lower;
    final int upper;
    final boolean isPositiveInfinity;
    final boolean isNegativeInfinity;

    public Range() {
        this(0, 0, true, true);
    }

    private Range(final int u, final int l, final boolean ip, final boolean in) {
        this.upper = u;
        this.lower = l;
        this.isPositiveInfinity = ip;
        this.isNegativeInfinity = in;
    }

    public boolean inRange(final int value) {
        boolean ret = true;
        if (!this.isPositiveInfinity) {
            ret = value < this.upper;
        }
        if (!this.isNegativeInfinity) {
            ret = ret && (value > this.lower);
        }
        return ret;
    }

    public Range setLower(final int l) {
        assert this.isNegativeInfinity || (l > this.lower);
        return new Range(this.upper, l, this.isPositiveInfinity, false);
    }

    public Range setUpper(final int u) {
        assert this.isPositiveInfinity || (u < this.upper);
        return new Range(u, this.lower, false, this.isNegativeInfinity);
    }
}

@SuppressWarnings("ALL")
public class TreeMap<V> {
    /**
     * Node in the Tree. Doubles as a means to pass key-value pairs back to user
     * (see Map.Entry).
     */

    static public class Entry<V> {
        int key;

        V value;

        Entry<V> left = null;

        Entry<V> right = null;

        Entry<V> parent;

        boolean color = TreeMap.BLACK;

        public Entry() {
            this.parent = null;
            this.value = null;
            this.key = -1;
        }

        /**
         * Make a new cell with given key, value, and parent, and with <tt>null</tt>
         * child links, and BLACK color.
         */
        Entry(final int key, final V value, final Entry<V> parent) {
            this.key = key;
            this.value = value;
            this.parent = parent;
        }

        /**
         * Returns true if black properties of tree are correct
         *
         * @post returns true if black properties of tree are correct
         */
        protected boolean blackConsistency() {

            if (this.color != TreeMap.BLACK) // root must be black
            {
                return false;
            }
            // the number of black nodes on way to any leaf must be same
            if (!consistentlyBlackHeight(blackHeight())) {
                return false;
            }
            return true;
        }

        /**
         * Returns the black height of this subtree.
         *
         * @pre
         * @post returns the black height of this subtree
         */
        private int blackHeight() {
            int ret = 0;
            if (this.color == TreeMap.BLACK) {
                ret = 1;
            }
            if (this.left != null) {
                ret += this.left.blackHeight();
            }
            return ret;
        }

        boolean consistency() {
            return wellConnected(null) && redConsistency() && blackConsistency()
                    && ordered();
        }

        /**
         * Checks to make sure that the black height of tree is height
         *
         * @post checks to make sure that the black height of tree is height
         */
        private boolean consistentlyBlackHeight(int height) {
            boolean ret = true;
            if (this.color == TreeMap.BLACK) {
                height--;
            }
            if (this.left == null) {
                ret = ret && (height == 0);
            } else {
                ret = ret && (this.left.consistentlyBlackHeight(height));
            }
            if (this.right == null) {
                ret = ret && (height == 0);
            } else {
                ret = ret && (this.right.consistentlyBlackHeight(height));
            }

            return ret;
        }

        @Override
        public boolean equals(final Object o) {
            if (!(o instanceof Entry)) {
                return false;
            }
            @SuppressWarnings("unchecked")
            final Entry e = (Entry) o;

            return (this.key == e.getKey())
                    && TreeMap.valEquals(this.value, e.getValue());
        }

        /**
         * Returns the key.
         *
         * @return the key.
         */
        public int getKey() {
            return this.key;
        }

        /**
         * Returns the value associated with the key.
         *
         * @return the value associated with the key.
         */
        public V getValue() {
            return this.value;
        }

        @Override
        public int hashCode() {
            final int keyHash = this.key;
            final int valueHash = (this.value == null ? 0 : this.value.hashCode());
            return keyHash ^ valueHash;
        }

        private boolean ordered() {
            return ordered(this, new Range());
        }

        private boolean ordered(final Entry<V> t, final Range range) {
            if (t == null) {
                return true;
            }
            if (!range.inRange(t.key)) {
                return false;
            }
            boolean ret = true;
            ret = ret && ordered(t.left, range.setUpper(t.key));
            ret = ret && ordered(t.right, range.setLower(t.key));
            return ret;
        }

        /**
         * Returns true if no red node in subtree has red children
         *
         * @post returns true if no red node in subtree has red children
         */
        private boolean redConsistency() {
            boolean ret = true;
            if ((this.color == TreeMap.RED)
                    && (((this.left != null) && (this.left.color == TreeMap.RED)) || ((this.right != null) && (this.right.color == TreeMap.RED)))) {
                return false;
            }
            if (this.left != null) {
                ret = ret && this.left.redConsistency();
            }
            if (this.right != null) {
                ret = ret && this.right.redConsistency();
            }
            return ret;
        }

        /**
         * Replaces the value currently associated with the key with the given
         * value.
         *
         * @return the value associated with the key before this method was called.
         */
        public V setValue(final V value) {
            final V oldValue = this.value;
            this.value = value;
            return oldValue;
        }

        int size() {
            int ls = 0, rs = 0;
            if (this.left != null) {
                ls = this.left.size();
            }
            if (this.right != null) {
                rs = this.right.size();
            }
            return 1 + ls + rs;
        }

        @Override
        public String toString() {
            return this.key + "=" + this.value;
        }

        /**
         * Returns true iff this tree is well-connected.
         */

        private boolean wellConnected(final Entry<V> expectedParent) {
            boolean ok = true;
            if (expectedParent != this.parent) {

                return false;
            }

            if (this.right != null) {
                // ok && is redundant because ok is assigned true
                ok = ok && this.right.wellConnected(this);
            }

            if (this.left != null) {

                ok = ok && this.left.wellConnected(this);
            }

            if ((this.right == this.left) && (this.right != null)
                    && (this.left != null)) {// left!=null
                // is
                // redundant
                // because
                // left==right
                // &&
                // right!=null
                return false;
            }

            return ok;
        }
    }

    private static final boolean RED = false;

    private static final boolean BLACK = true;

    /**
     * Balancing operations.
     *
     * Implementations of rebalancings during insertion and deletion are slightly
     * different than the CLR version. Rather than using dummy nilnodes, we use a
     * set of accessors that deal properly with null. They are used to avoid
     * messiness surrounding nullness checks in the main algorithms.
     */

    private static <V> boolean colorOf(final Entry<V> p) {
        return (p == null ? TreeMap.BLACK : p.color);
    }

    /**
     * Returns the key corresponding to the specified Entry. Throw
     * NoSuchElementException if the Entry is <tt>null</tt>.
     */
    private static int key(final Entry<?> e) {
        if (e == null) {
            throw new NoSuchElementException();
        }
        return e.key;
    }

    // Query Operations

    private static <V> Entry<V> leftOf(final Entry<V> p) {
        return (p == null) ? null : p.left;
    }

    private static <V> Entry<V> parentOf(final Entry<V> p) {
        return (p == null ? null : p.parent);
    }

    private static <V> Entry<V> rightOf(final Entry<V> p) {
        return (p == null) ? null : p.right;
    }

    private static <V> void setColor(final Entry<V> p, final boolean c) {
        if (p != null) {
            p.color = c;
        }
    }

    /**
     * Test two values for equality. Differs from o1.equals(o2) only in that it
     * copes with <tt>null</tt> o1 properly.
     */
    private static boolean valEquals(final Object o1, final Object o2) {
        return (o1 == null ? o2 == null : o1.equals(o2));
    }

    /**
     * The Comparator used to maintain order in this TreeMapGeneric, or null if
     * this TreeMapGeneric uses its elements natural ordering.
     *
     * @serial
     */

    private transient Entry<V> root = null;

    /**
     * The number of entries in the tree
     */
    private transient int size = 0;

    /**
     * The number of structural modifications to the tree.
     */
    private transient int modCount = 0;

    /**
     * Removes all mappings from this TreeMapGeneric.
     */
    public void clear() {
        this.modCount++;
        this.size = 0;
        this.root = null;
    }

    /**
     * Compares two keys using the correct comparison method for this
     * TreeMapGeneric.
     */
    private int compare(final int k1, final int k2) {
        if (k1 < k2) {
            return -1;

        } else if (k1 == k2) {
            return 0;
        } else {
            return 1;
        }
    }

    /**
     * Returns <tt>true</tt> if this map contains a mapping for the specified key.
     *
     * @param key
     *          key whose presence in this map is to be tested.
     *
     * @return <tt>true</tt> if this map contains a mapping for the specified key.
     * @throws ClassCastException
     *           if the key cannot be compared with the keys currently in the map.
     * @throws NullPointerException
     *           key is <tt>null</tt> and this map uses natural ordering, or its
     *           comparator does not tolerate <tt>null</tt> keys.
     */
    public boolean containsKey(final int key) {
        return getEntry(key) != null;
    }

    /**
     * Returns <tt>true</tt> if this map maps one or more keys to the specified
     * value. More formally, returns <tt>true</tt> if and only if this map
     * contains at least one mapping to a value <tt>value</tt> such that
     * <tt>(value==null ? value==null : value.equals(value))</tt>. This operation
     * will probably require time linear in the Map size for most implementations
     * of Map.
     *
     * @param value
     *          value whose presence in this Map is to be tested.
     * @return <tt>true</tt> if a mapping to <tt>value</tt> exists; <tt>false</tt>
     *         otherwise.
     * @since 1.2
     */
    public boolean containsValue(final Object value) {
        return (this.root == null ? false
                : (value == null ? valueSearchNull(this.root) : valueSearchNonNull(
                this.root, value)));
    }

    private void decrementSize() {
        this.modCount++;
        this.size--;
    }

    /**
     * Delete node p, and then rebalance the tree.
     */

    private void deleteEntry(Entry<V> p) {
        decrementSize();

        // If strictly internal, copy successor's element to p and then make p
        // point to successor.
        if ((p.left != null) && (p.right != null)) {
            final Entry<V> s = successor(p);
            p.key = s.key;
            p.value = s.value;
            p = s;
        } // p has 2 children

        // Start fixup at replacement node, if it exists.
        final Entry<V> replacement = (p.left != null ? p.left : p.right);

        if (replacement != null) {
            // Link replacement to parent
            replacement.parent = p.parent;
            if (p.parent == null) {
                this.root = replacement;
            } else if (p == p.parent.left) {
                p.parent.left = replacement;
            } else {
                p.parent.right = replacement;
            }

            // Null out links so they are OK to use by fixAfterDeletion.
            p.left = p.right = p.parent = null;

            // Fix replacement
            if (p.color == TreeMap.BLACK) {
                fixAfterDeletion(replacement);
            }
        } else if (p.parent == null) { // return if we are the only node.
            this.root = null;
        } else { // No children. Use self as phantom replacement and unlink.
            if (p.color == TreeMap.BLACK) {
                fixAfterDeletion(p);
            }

            if (p.parent != null) {
                if (p == p.parent.left) {
                    p.parent.left = null;
                } else if (p == p.parent.right) {
                    p.parent.right = null;
                }
                p.parent = null;
            }
        }
    }

    /**
     * Returns the first Entry in the TreeMapGeneric (according to the
     * TreeMapGeneric's key-sort function). Returns null if the TreeMapGeneric is
     * empty.
     */
    private Entry<V> firstEntry() {
        Entry<V> p = this.root;
        if (p != null) {
            while (p.left != null) {
                p = p.left;
            }
        }
        return p;
    }

    /**
     * Returns the first (lowest) key currently in this sorted map.
     *
     * @return the first (lowest) key currently in this sorted map.
     * @throws NoSuchElementException
     *           Map is empty.
     */
    public int firstKey() {
        return TreeMap.key(firstEntry());
    }

    /** From CLR * */
    private void fixAfterDeletion(Entry<V> x) {
        while ((x != this.root) && (TreeMap.colorOf(x) == TreeMap.BLACK)) {
            if (x == TreeMap.leftOf(TreeMap.parentOf(x))) {
                Entry<V> sib = TreeMap.rightOf(TreeMap.parentOf(x));

                if (TreeMap.colorOf(sib) == TreeMap.RED) {
                    TreeMap.setColor(sib, TreeMap.BLACK);
                    TreeMap.setColor(TreeMap.parentOf(x), TreeMap.RED);
                    rotateLeft(TreeMap.parentOf(x));
                    sib = TreeMap.rightOf(TreeMap.parentOf(x));
                }

                if ((TreeMap.colorOf(TreeMap.leftOf(sib)) == TreeMap.BLACK)
                        && (TreeMap.colorOf(TreeMap.rightOf(sib)) == TreeMap.BLACK)) {
                    TreeMap.setColor(sib, TreeMap.RED);
                    x = TreeMap.parentOf(x);
                } else {
                    if (TreeMap.colorOf(TreeMap.rightOf(sib)) == TreeMap.BLACK) {
                        TreeMap.setColor(TreeMap.leftOf(sib), TreeMap.BLACK);
                        TreeMap.setColor(sib, TreeMap.RED);
                        rotateRight(sib);
                        sib = TreeMap.rightOf(TreeMap.parentOf(x));
                    }
                    TreeMap.setColor(sib, TreeMap.colorOf(TreeMap.parentOf(x)));
                    TreeMap.setColor(TreeMap.parentOf(x), TreeMap.BLACK);
                    TreeMap.setColor(TreeMap.rightOf(sib), TreeMap.BLACK);
                    rotateLeft(TreeMap.parentOf(x));
                    x = this.root;
                }
            } else { // symmetric
                Entry<V> sib = TreeMap.leftOf(TreeMap.parentOf(x));

                if (TreeMap.colorOf(sib) == TreeMap.RED) {
                    TreeMap.setColor(sib, TreeMap.BLACK);
                    TreeMap.setColor(TreeMap.parentOf(x), TreeMap.RED);
                    rotateRight(TreeMap.parentOf(x));
                    sib = TreeMap.leftOf(TreeMap.parentOf(x));
                }

                if ((TreeMap.colorOf(TreeMap.rightOf(sib)) == TreeMap.BLACK)
                        && (TreeMap.colorOf(TreeMap.leftOf(sib)) == TreeMap.BLACK)) {
                    TreeMap.setColor(sib, TreeMap.RED);
                    x = TreeMap.parentOf(x);
                } else {
                    if (TreeMap.colorOf(TreeMap.leftOf(sib)) == TreeMap.BLACK) {
                        TreeMap.setColor(TreeMap.rightOf(sib), TreeMap.BLACK);
                        TreeMap.setColor(sib, TreeMap.RED);
                        rotateLeft(sib);
                        sib = TreeMap.leftOf(TreeMap.parentOf(x));
                    }
                    TreeMap.setColor(sib, TreeMap.colorOf(TreeMap.parentOf(x)));
                    TreeMap.setColor(TreeMap.parentOf(x), TreeMap.BLACK);
                    TreeMap.setColor(TreeMap.leftOf(sib), TreeMap.BLACK);
                    rotateRight(TreeMap.parentOf(x));
                    x = this.root;
                }
            }
        }

        TreeMap.setColor(x, TreeMap.BLACK);
    }

    /** From CLR * */
    private void fixAfterInsertion(Entry<V> x) {
        x.color = TreeMap.RED;

        while ((x != null) && (x != this.root) && (x.parent.color == TreeMap.RED)) {
            if (TreeMap.parentOf(x) == TreeMap.leftOf(TreeMap.parentOf(TreeMap
                    .parentOf(x)))) {
                final Entry<V> y = TreeMap.rightOf(TreeMap
                        .parentOf(TreeMap.parentOf(x)));
                if (TreeMap.colorOf(y) == TreeMap.RED) {
                    TreeMap.setColor(TreeMap.parentOf(x), TreeMap.BLACK);
                    TreeMap.setColor(y, TreeMap.BLACK);
                    TreeMap.setColor(TreeMap.parentOf(TreeMap.parentOf(x)), TreeMap.RED);
                    x = TreeMap.parentOf(TreeMap.parentOf(x));
                } else {
                    if (x == TreeMap.rightOf(TreeMap.parentOf(x))) {
                        x = TreeMap.parentOf(x);
                        rotateLeft(x);
                    }
                    TreeMap.setColor(TreeMap.parentOf(x), TreeMap.BLACK);// bug
                    // seeded
                    TreeMap.setColor(TreeMap.parentOf(TreeMap.parentOf(x)), TreeMap.RED);
                    if (TreeMap.parentOf(TreeMap.parentOf(x)) != null) {
                        rotateRight(TreeMap.parentOf(TreeMap.parentOf(x)));
                    }
                }
            } else {
                final Entry<V> y = TreeMap
                        .leftOf(TreeMap.parentOf(TreeMap.parentOf(x)));
                if (TreeMap.colorOf(y) == TreeMap.RED) {
                    TreeMap.setColor(TreeMap.parentOf(x), TreeMap.BLACK);
                    TreeMap.setColor(y, TreeMap.BLACK);
                    TreeMap.setColor(TreeMap.parentOf(TreeMap.parentOf(x)), TreeMap.RED);
                    x = TreeMap.parentOf(TreeMap.parentOf(x));
                } else {
                    if (x == TreeMap.leftOf(TreeMap.parentOf(x))) {
                        x = TreeMap.parentOf(x);
                        rotateRight(x);
                    }
                    TreeMap.setColor(TreeMap.parentOf(x), TreeMap.BLACK);
                    TreeMap.setColor(TreeMap.parentOf(TreeMap.parentOf(x)), TreeMap.RED);
                    if (TreeMap.parentOf(TreeMap.parentOf(x)) != null) {
                        rotateLeft(TreeMap.parentOf(TreeMap.parentOf(x)));
                    }
                }
            }
        }
        this.root.color = TreeMap.BLACK;
    }

    /**
     * Returns the value to which this map maps the specified key. Returns
     * <tt>null</tt> if the map contains no mapping for this key. A return value
     * of <tt>null</tt> does not <i>necessarily</i> indicate that the map contains
     * no mapping for the key; it's also possible that the map explicitly maps the
     * key to <tt>null</tt>. The <tt>containsKey</tt> operation may be used to
     * distinguish these two cases.
     *
     * @param key
     *          key whose associated value is to be returned.
     * @return the value to which this map maps the specified key, or
     *         <tt>null</tt> if the map contains no mapping for the key.
     * @throws ClassCastException
     *           key cannot be compared with the keys currently in the map.
     * @throws NullPointerException
     *           key is <tt>null</tt> and this map uses natural ordering, or its
     *           comparator does not tolerate <tt>null</tt> keys.
     *
     * @see #containsKey(Object)
     */
    //@ requires ((this.root == null) || this.root.consistency()) && (this.size == this.realSize());
    //@ ensures true;
    public V get(final int key) {
        final Entry<V> p = getEntry(key);
        return (p == null ? null : p.value);
    }

    /**
     * Returns this map's entry for the given key, or <tt>null</tt> if the map
     * does not contain an entry for the key.
     *
     * @return this map's entry for the given key, or <tt>null</tt> if the map
     *         does not contain an entry for the key.
     * @throws ClassCastException
     *           if the key cannot be compared with the keys currently in the map.
     * @throws NullPointerException
     *           key is <tt>null</tt> and this map uses natural order, or its
     *           comparator does not tolerate * <tt>null</tt> keys.
     */
    private Entry<V> getEntry(final int key) {
        Entry<V> p = this.root;
        final int k = key;
        while (p != null) {
            // int cmp = compare(k, p.key);
            if (k == p.key) {
                return p;
            } else if (k < p.key) {
                p = p.left;
            } else {
                p = p.right;
            }
        }
        return null;
    }

    private void incrementSize() {
        this.modCount++;
        this.size++;
    }

    /**
     * Returns the last Entry in the TreeMapGeneric (according to the
     * TreeMapGeneric's key-sort function). Returns null if the TreeMapGeneric is
     * empty.
     */
    private Entry<V> lastEntry() {
        Entry<V> p = this.root;
        if (p != null) {
            while (p.right != null) {
                p = p.right;
            }
        }
        return p;
    }

    /**
     * Returns the last (highest) key currently in this sorted map.
     *
     * @return the last (highest) key currently in this sorted map.
     * @throws NoSuchElementException
     *           Map is empty.
     */
    //@ requires ((this.root == null) || this.root.consistency()) && (this.size == this.realSize());
    //@ ensures true;
    public int lastKey() {
        return TreeMap.key(lastEntry());
    }

    //@ requires ((this.root == null) || this.root.consistency()) && (this.size == this.realSize());
    //@ ensures ((this.root != null) && this.root.consistency());
    public V put(final int key, final V value) {
        Entry<V> t = this.root;

        if (t == null) {
            incrementSize();
            this.root = new Entry<V>(key, value, null);
            return null;
        }

        while (true) {
            final int cmp = compare(key, t.key);
            if (cmp == 0) {
                return t.setValue(value);
            } else if (cmp < 0) {
                if (t.left != null) {
                    t = t.left;
                } else {
                    incrementSize();
                    t.left = new Entry<V>(key, value, t);
                    fixAfterInsertion(t.left);
                    return null;
                }
            } else { // cmp > 0
                if (t.right != null) {
                    t = t.right;
                } else {
                    incrementSize();
                    t.right = new Entry<V>(key, value, t);
                    fixAfterInsertion(t.right);
                    return null;
                }
            }
        }
        // return null;
    }

    /**
     * Associates the specified value with the specified key in this map. If the
     * map previously contained a mapping for this key, the old value is replaced.
     *
     * @param key
     *          key with which the specified value is to be associated.
     * @param value
     *          value to be associated with the specified key.
     *
     * @return previous value associated with specified key, or <tt>null</tt> if
     *         there was no mapping for key. A <tt>null</tt> return can also
     *         indicate that the map previously associated <tt>null</tt> with the
     *         specified key.
     * @throws ClassCastException
     *           key cannot be compared with the keys currently in the map.
     * @throws NullPointerException
     *           key is <tt>null</tt> and this map uses natural order, or its
     *           comparator does not tolerate <tt>null</tt> keys.
     */

    public int realSize() {
        if (this.root == null) {
            return 0;
        }
        return this.root.size();
    }

    /**
     * Removes the mapping for this key from this TreeMapGeneric if present.
     *
     * @param key
     *          key for which mapping should be removed
     * @return previous value associated with specified key, or <tt>null</tt> if
     *         there was no mapping for key. A <tt>null</tt> return can also
     *         indicate that the map previously associated <tt>null</tt> with the
     *         specified key.
     *
     * @throws ClassCastException
     *           key cannot be compared with the keys currently in the map.
     * @throws NullPointerException
     *           key is <tt>null</tt> and this map uses natural order, or its
     *           comparator does not tolerate <tt>null</tt> keys.
     */
    //@ requires ((this.root == null) || this.root.consistency()) && (this.size == this.realSize());
    //@ ensures ((this.root == null) || this.root.consistency()) && (this.size == this.realSize());
    public V remove(final int key) {
        final Entry<V> p = getEntry(key);
        if (p == null) {
            return null;
        }

        final V oldValue = p.value;
        deleteEntry(p);
        return oldValue;
    }

    /** From CLR * */
    private void rotateLeft(final Entry<V> p) {
        final Entry<V> r = p.right;
        p.right = r.left;
        if (r.left != null) {
            r.left.parent = p;
        }
        r.parent = p.parent;
        if (p.parent == null) {
            this.root = r;
        } else if (p.parent.left == p) {
            p.parent.left = r;
        } else {
            p.parent.right = r;
        }
        r.left = p;
        p.parent = r;
    }

    /** From CLR * */
    private void rotateRight(final Entry<V> p) {
        final Entry<V> l = p.left;
        p.left = l.right;
        if (l.right != null) {
            l.right.parent = p;
        }
        l.parent = p.parent;
        if (p.parent == null) {
            this.root = l;
        } else if (p.parent.right == p) {
            p.parent.right = l;
        } else {
            p.parent.left = l;
        }
        l.right = p;
        p.parent = l;
    }

    /**
     * Returns the number of key-value mappings in this map.
     *
     * @return the number of key-value mappings in this map.
     */

    public int size() {
        return this.size;
    }

    /**
     * Returns the successor of the specified Entry, or null if no such.
     */
    private Entry<V> successor(final Entry<V> t) {
        if (t == null) {
            return null;
        } else if (t.right != null) {
            Entry<V> p = t.right;
            while (p.left != null) {
                p = p.left;
            }
            return p;
        } else {
            Entry<V> p = t.parent;
            Entry<V> ch = t;
            while ((p != null) && (ch == p.right)) {
                ch = p;
                p = p.parent;
            }
            return p;
        }
    }

    private boolean valueSearchNonNull(final Entry<V> n, final Object value) {
        // Check this node for the value
        if (value.equals(n.value)) {
            return true;
        }

        // Check left and right subtrees for value
        return ((n.left != null) && valueSearchNonNull(n.left, value))
                || ((n.right != null) && valueSearchNonNull(n.right, value));
    }

    private boolean valueSearchNull(final Entry<V> n) {
        if (n.value == null) {
            return true;
        }

        // Check left and right subtrees for value
        return ((n.left != null) && valueSearchNull(n.left))
                || ((n.right != null) && valueSearchNull(n.right));
    }

}
