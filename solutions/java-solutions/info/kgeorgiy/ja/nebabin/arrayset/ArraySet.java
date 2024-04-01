package info.kgeorgiy.ja.nebabin.arrayset;

import java.util.*;

public class ArraySet<E> extends AbstractSet<E> implements NavigableSet<E>, List<E> {
    protected final List<E> elems;
    protected final Comparator<? super E> comp;

    public ArraySet() {
        this((Comparator<E>) null);
    }

    public ArraySet(Comparator<? super E> comp) {
        this(Collections.emptyList(), comp);
    }

    public ArraySet(Collection<? extends E> col) {
        this(col, null);
    }

    public ArraySet(Collection<? extends E> col, Comparator<? super E> comp) {
        this(Collections.unmodifiableList(new ArrayList<>(createTreeSet(col, comp))), comp);
    }

    public ArraySet(SortedSet<E> set) {
        this(Collections.unmodifiableList(new ArrayList<>(set)), set.comparator());
    }

    private ArraySet(List<E> elems, Comparator<? super E> comp) {
        this.elems = elems;
        this.comp = comp;
    }

    private int lowerBound(E key, boolean inclusive) {
        return upperBound(key, !inclusive) - 1;
    }

    private int upperBound(E key, boolean inclusive) {
        int binRes = Collections.binarySearch(elems, key, comp);
        if (binRes >= 0 && !inclusive) {
            ++binRes;
        }
        return binRes >= 0 ? binRes : -(binRes + 1);
    }

    private E getCheckedElem(int ind) {
        return 0 <= ind && ind < size() ? elems.get(ind) : null;
    }

    @Override
    public boolean contains(Object elem) {
        return indexOf(elem) != -1;
    }

    @Override
    public E lower(E e) {
        return getCheckedElem(lowerBound(e, false));
    }

    @Override
    public E floor(E e) {
        return getCheckedElem(lowerBound(e, true));
    }

    @Override
    public E ceiling(E e) {
        return getCheckedElem(upperBound(e, true));
    }

    @Override
    public E higher(E e) {
        return getCheckedElem(upperBound(e, false));
    }

    @Override
    public E pollFirst() {
        throw new UnsupportedOperationException("Method pollFirst is unsupported.");
    }

    @Override
    public E pollLast() {
        throw new UnsupportedOperationException("Method pollLast is unsupported.");
    }

    @Override
    public Iterator<E> iterator() {
        return elems.iterator();
    }

    @Override
    public boolean addAll(int index, Collection<? extends E> c) {
        throw getException();
    }

    @Override
    public E get(int index) {
        return elems.get(index);
    }

    @Override
    public E set(int index, E element) {
        throw getException();
    }

    @Override
    public void add(int index, E element) {
        throw getException();
    }

    @Override
    public E remove(int index) {
        throw getException();
    }

    @Override
    @SuppressWarnings("unchecked")
    public int indexOf(Object o) {
        int binRes = Collections.binarySearch(elems, (E) o, comp);
        return binRes >= 0 ? binRes : -1;
    }

    @Override
    public int lastIndexOf(Object o) {
        return indexOf(o);
    }

    @Override
    public ListIterator<E> listIterator() {
        return elems.listIterator();
    }

    @Override
    public ListIterator<E> listIterator(int index) {
        return elems.listIterator(index);
    }

    @Override
    public List<E> subList(int fromIndex, int toIndex) {
        return elems.subList(fromIndex, toIndex);
    }

    @Override
    public NavigableSet<E> descendingSet() {
        return reversed();
    }

    @Override
    public ArraySet<E> reversed() {
        return new ArraySet<>(elems.reversed(), Collections.reverseOrder(comp));
    }

    @Override
    public Iterator<E> descendingIterator() {
        return elems.reversed().iterator();
    }

    @Override
    public NavigableSet<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
        if (Collections.reverseOrder(comp).compare(fromElement, toElement) < 0) {
            throw new IllegalArgumentException("Trying to get subset of invalid range");
        }
        int fromInd = upperBound(fromElement, fromInclusive);
        int toInd = upperBound(toElement, !toInclusive);
        return new ArraySet<>(elems.subList(Integer.min(fromInd, toInd), toInd), comp);
    }

    @Override
    public NavigableSet<E> headSet(E toElement, boolean inclusive) {
        int toInd = upperBound(toElement, !inclusive);
        return new ArraySet<>(elems.subList(0, toInd), comp);
    }

    @Override
    public NavigableSet<E> tailSet(E fromElement, boolean inclusive) {
        int fromInd = upperBound(fromElement, inclusive);
        return new ArraySet<>(elems.subList(fromInd, size()), comp);
    }

    @Override
    public Comparator<? super E> comparator() {
        return comp;
    }

    @Override
    public SortedSet<E> subSet(E fromElement, E toElement) {
        return subSet(fromElement, true, toElement, false);
    }

    @Override
    public SortedSet<E> headSet(E toElement) {
        return headSet(toElement, false);
    }

    @Override
    public SortedSet<E> tailSet(E fromElement) {
        return tailSet(fromElement, true);
    }

    @Override
    public E removeFirst() {
        throw getException();
    }

    @Override
    public E removeLast() {
        throw getException();
    }

    @Override
    public E first() {
        return elems.getFirst();
    }

    @Override
    public E getFirst() {
        return first();
    }

    @Override
    public E last() {
        return elems.getLast();
    }

    @Override
    public Spliterator<E> spliterator() {
        return elems.spliterator();
    }

    @Override
    public void addFirst(E e) {
        throw getException();
    }

    @Override
    public void addLast(E e) {
        throw getException();
    }

    @Override
    public E getLast() {
        return last();
    }

    @Override
    public int size() {
        return elems.size();
    }
    private static <E> TreeSet<E> createTreeSet(Collection<? extends E> col, Comparator<? super E> comp) {
        TreeSet<E> res = new TreeSet<>(comp);
        res.addAll(col);
        return res;
    }

    private static UnsupportedOperationException getException() {
        return new UnsupportedOperationException("Trying to change unmodifiable collection.");
    }
}
