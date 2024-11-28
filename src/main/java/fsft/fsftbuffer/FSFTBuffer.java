package fsft.fsftbuffer;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A finite-space, finite-time buffer of objects. Each object in the buffer is {@link Bufferable}
 * (has a unique id). A buffer has a capacity, which is the number of objects it can hold
 * at a time, and a timeout duration, which is the duration of time an object can remain
 * in the buffer before it is considered "expired" and is removed. When the buffer is full,
 * adding a new item evicts the LRU (least recently used) item from the buffer. Items are
 * considered used if they are "gotten" by calling the {@code get} method. Items can be
 * "refreshed" (increase their lifespan) by calling the {@code touch} method on them.
 * Attempting to call {@code put} on an existing object in the buffer also refreshes its
 * expiry time.
 *
 * <p>Main methods provided:</p>
 * <ul>
 *   <li>{@link #put(Bufferable)} puts a new object in the buffer</li>
 *   <li>{@link #get(String)} gets the item in the buffer with the given id, effectively
 *   "using" it</li>
 *   <li>{@link #touch(String)} updates the expiry/timeout time of the object with the
 *   given id to (current time) + {@link #delta}</li>
 * </ul>
 *
 * <p>Notes:</p>
 * <ul>
 *     <li>{@code FSFTBuffer} is thread-safe. Instances of this class can be accessed by
 *     multiple threads concurrently</li>
 *     <li>When the buffer reaches its capacity and a new object is added, the buffer evicts
 *     the least recently used (LRU) item</li>
 *     <li>Buffer capacity and delta are fixed at creation and cannot be modified after</li>
 * </ul>
 *
 * @param <B> the type of objects in the buffer; implements the {@link Bufferable} interface
 * */

public class FSFTBuffer<B extends Bufferable> {

    // Abstraction Function:
    //      AF(r) = A finite-space finite-time buffer where:
    //      - r.capacity is the capacity of the buffer, specified during creation
    //      - r.delta is the timeout duration, aka the amount of time an object can spend in
    //      the buffer
    //      - r.timeoutMap maps the items to their expiry times
    //      - r.idMap maps the items' ids to the item itself
    //      - r.lastUseQueue tracks the use frequency of items

    // Rep Invariant is
    //      capacity > 0
    //      delta is not null and is a positive time duration
    //      lastUseQueue, timeoutMap, and idMap are not null
    //      lastUseQueue does not contain null values
    //      timeoutMap and idMap do not contain null keys or values
    //      lastUseQueue, timeoutMap.keySet(), and idMap.values() contain the same elements

    /* the default buffer size is 32 objects */
    public static final int DEFAULT_CAPACITY = 32;
    /* the default timeout value is 180 seconds */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(180);

    private final Map<B, Long> timeoutMap = new ConcurrentHashMap<>();
    private final Map<String, B> idMap = new ConcurrentHashMap<>();
    private final Queue<B> lastUseQueue = new ConcurrentLinkedQueue<>();

    private final int capacity;
    private final Duration delta;

    /**
     * Create a buffer with a fixed capacity and a timeout value.
     * Objects in the buffer that have not been refreshed within the
     * timeout period are removed from the cache.
     *
     * @param capacity the number of objects the buffer can hold
     * @param delta  the duration, in seconds, an object should
     *                 be in the buffer before it times out
     */
    public FSFTBuffer(int capacity, Duration delta) {
        this.capacity = capacity;
        this.delta = delta;
    }

    /**
     * Create a buffer with default capacity and timeout values.
     */
    public FSFTBuffer() {
        this(DEFAULT_CAPACITY, DEFAULT_TIMEOUT);
    }

    /**
     * Add a value to the buffer.
     * If the buffer is full then remove the least recently accessed
     * object to make room for the new object.
     * This method can be used to replace an object in the buffer with
     * a newer instance. {@code b} is uniquely identified by its id,
     * {@code b.id()}.
     */
    public boolean put(B b) {
        if (b == null) {
            throw new IllegalArgumentException("Object cannot be null");
        }
        synchronized (this) {
            if (touch(b.id())) { // checks for existence and updates timeout if existing
                System.out.println("attempted to add duplicate: " + b.id());
                return false;
            }
            if (lastUseQueue.size() >= capacity) {
                System.out.println("BUFFER FULL");
                if (!refresh()) {
                    removeLRU();
                }
            }
            timeoutMap.put(b, System.currentTimeMillis() + delta.toMillis());
            lastUseQueue.add(b);
            idMap.put(b.id(), b);
            System.out.println("added item " + b.id() + " - " + lastUseQueue.size() + " items in buffer");
            return true;
        }
    }

    /**
     * @param id the identifier of the object to be retrieved
     * @return the object that matches the identifier from the
     * buffer
     */
    public B get(String id) {
        if (id == null) {
            throw new IllegalArgumentException("ID cannot be null");
        }
        if (!idMap.containsKey(id)) {
            throw new NoSuchElementException("Buffer does not contain item with given id");
        }
        synchronized (this) {
            B item = idMap.get(id);
            if (isExpired(idMap.get(id))) {
                throw new NoSuchElementException("Buffer does not contain item with given id");
            }
            lastUseQueue.remove(item);
            lastUseQueue.add(item);
        System.out.println("got item " + id);
            return idMap.get(id);
        }
    }

    /**
     * Checks if the given item is expired, and removes it if it is.
     *
     * @param item to check the freshness of
     * @return true if the item is expired and was removed, false otherwise
     */
    private boolean isExpired(B item) {
        if (System.currentTimeMillis() > timeoutMap.get(item)) {
            idMap.remove(item.id());
            timeoutMap.remove(item);
            lastUseQueue.remove(item);
            return true;
        }
        return false;
    }

    /**
     * Update the last refresh time for the object with the provided id.
     * This method is used to mark an object as "not stale" so that its
     * timeout is delayed.
     *
     * @param id the identifier of the object to "touch"
     * @return true if successful and false otherwise
     */
    public boolean touch(String id) {
        if (id == null) {
            throw new IllegalArgumentException("ID cannot be null");
        }
        synchronized (this) {
            if (!idMap.containsKey(id)) {
                return false;
            }
            B item = idMap.get(id);
            if (isExpired(idMap.get(id))) {
                return false;
            }
            timeoutMap.put(item, System.currentTimeMillis() + delta.toMillis());
        System.out.println("touched " + id);
            return true;
        }
    }

    /**
     * Removes the LRU (least recently used) object from the buffer
     */
    private void removeLRU() {
        B removedItem = lastUseQueue.remove();
        idMap.remove(removedItem.id());
        timeoutMap.remove(removedItem);
        System.out.println("removed LRU: " + removedItem.id());
    }

    /**
     * Refreshes the buffer, removing all expired entries.
     *
     * @return true if items were removed, false otherwise
     */
    private boolean refresh() {
        Iterator<Map.Entry<B, Long>> it = timeoutMap.entrySet().iterator();
        boolean removed = false;
        while (it.hasNext()) {
            Map.Entry<B, Long> entry = it.next();
            if (System.currentTimeMillis() > entry.getValue()) {
                it.remove();
                idMap.remove(entry.getKey().id());
                lastUseQueue.remove(entry.getKey());
                removed = true;
                System.out.println("removed expired: " + entry.getKey().id());
            }
        }
        return removed;
    }

    /**
     * @return a set of a all the items currently in the buffer
     */
    public Set<B> currentItems() {
        synchronized (this) {
            refresh();
        }
        return Collections.unmodifiableSet(timeoutMap.keySet());
    }
}
