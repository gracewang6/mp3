package fsft.fsftbuffer;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class FSFTBuffer<B extends Bufferable> {

    // Rep Invariant is
    //      capacity > 0
    //

    /* the default buffer size is 32 objects */
    public static final int DEFAULT_CAPACITY = 32;

    /* the default timeout value is 180 seconds */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(180);

    private final int capacity;
    private final Duration delta;
    private final Map<B, Long> timeoutMap;
    private final Map<String, B> idMap;
    private final Queue<B> lastUseQueue;
    private final long t0;
    private final Thread parentThread; // thread where this instance was created

    /* TODO: Implement this datatype */

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
        parentThread = Thread.currentThread();
        t0 = System.currentTimeMillis();
        this.capacity = capacity;
        this.delta = delta;
        idMap = new ConcurrentHashMap<>();
        timeoutMap = new ConcurrentHashMap<>();
        lastUseQueue = new ConcurrentLinkedQueue<>();
        Thread refresher = new Thread(new Runnable() {
            @Override
            public void run() {
                while (parentThread.isAlive()) {
                    refresh();
                }
            }
        });
        refresher.start();
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
        if (touch(b.id())) {
            return false;
        }
        if (lastUseQueue.size() >= capacity) {
            removeLru();
        }
        timeoutMap.put(b, System.currentTimeMillis() + delta.toMillis());
        lastUseQueue.add(b);
        idMap.put(b.id(), b);
        return true;
    }

    private void removeLru() {
        B removedItem = lastUseQueue.remove();
        idMap.remove(removedItem.id());
        timeoutMap.remove(removedItem);
    }

    private void refresh() {
        Iterator<Map.Entry<B, Long>> it = timeoutMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<B, Long> entry = it.next();
            if (System.currentTimeMillis() > entry.getValue()) {
                it.remove();
                idMap.remove(entry.getKey().id());
                lastUseQueue.remove(entry.getKey());
                System.out.println("removed " + entry.getKey().id());
                System.out.println("time: " + (System.currentTimeMillis() - t0) + " ms after buffer creation");
            }
        }
    }

    /**
     * @param id the identifier of the object to be retrieved
     * @return the object that matches the identifier from the
     * buffer
     */
    public B get(String id) {
        if (!idMap.containsKey(id)) {
            throw new NoSuchElementException("Buffer does not contain item with given id");
        }
        B item = idMap.get(id);
        lastUseQueue.remove(item);
        lastUseQueue.add(item);
        return idMap.get(id);
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
        if (!idMap.containsKey(id)) {
            return false;
        }
        B b = idMap.get(id);
        timeoutMap.put(b, System.currentTimeMillis() + delta.toMillis());
        return true;
    }

    public static void main(String[] args) throws InterruptedException {
        FSFTBuffer<SimpleBufferableItem> buff = new FSFTBuffer<>(4, Duration.ofSeconds(3));
        SimpleBufferableItem i1 = new SimpleBufferableItem("i1");
        SimpleBufferableItem i2 = new SimpleBufferableItem("i2");
        SimpleBufferableItem i3 = new SimpleBufferableItem("i3");
        SimpleBufferableItem i4 = new SimpleBufferableItem("i4");
        buff.put(i1);
        buff.put(i2);
        Thread.sleep(3000);
        buff.put(i3);
        buff.put(i4);
        Thread.sleep(10000);
    }
}
