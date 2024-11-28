package fsft.fsftbuffer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class FSFTBufferTests {
    private FSFTBuffer<SimpleBufferableItem> buff;
    private SimpleBufferableItem b1;
    private SimpleBufferableItem b2;
    private SimpleBufferableItem b3;
    private SimpleBufferableItem b4;

    @BeforeEach
    public void init() {
        buff = new FSFTBuffer<>(3, Duration.ofMillis(1000));
        b1 = new SimpleBufferableItem("item 1");
        b2 = new SimpleBufferableItem("item 2");
        b3 = new SimpleBufferableItem("item 3");
        b4 = new SimpleBufferableItem("item 4");
    }

    @Test
    public void test() throws InterruptedException {
        assertTrue(buff.put(b1));
        assertTrue(buff.put(b2));
        assertTrue(buff.put(b3));
        Thread.sleep(800);
        buff.touch("item 1"); // refresh item 1
        assertFalse(buff.put(b2)); // item 2 already in buffer, this also refreshes it
        Thread.sleep(800);
        assertTrue(buff.put(b4));
        assertEquals(Set.of(b1, b2, b4), buff.currentItems()); // item 3 has expired
    }

    @Test
    public void test_RemoveLRUWhenFull() {
        buff.put(b1);
        buff.put(b2);
        buff.put(b3);
        buff.put(b4);
        assertFalse(buff.touch("item 1")); // item 1 was least recently accessed, so is removed
        buff.get("item 2"); // item 2 gets used
        buff.put(b1); // put item 1 back
        assertFalse(buff.touch("item 3")); // item 3 should be gone
    }

    @Test
    public void test_Get() throws InterruptedException {
        buff.put(b1);
        assertEquals(b1, buff.get("item 1"));
        Thread.sleep(4000);
        assertThrows(NoSuchElementException.class, () -> buff.get("item 1"));
    }

    @Test
    public void test_Null() {
        assertThrows(IllegalArgumentException.class, () -> buff.put(null));
        assertThrows(IllegalArgumentException.class, () -> buff.get(null));
        assertThrows(IllegalArgumentException.class, () -> buff.touch(null));
    }

    @Test
    public void test_Multithreading() throws InterruptedException {
        FSFTBuffer<SimpleBufferableItem> buff = new FSFTBuffer<>(5, Duration.ofMillis(1));
        int threadCount = 20;
        int itemCount = 20;
        Set<Thread> threads = new HashSet<>();
        AtomicBoolean failed = new AtomicBoolean(false);
        for (int i = 0; i < threadCount; i++) {
            int threadNumber = i;
            Thread t = new Thread(() -> {
                try {
                    for (int j = 0; j < itemCount; j++) {
                        if (failed.get()) {
                            System.out.println("thread " + threadNumber + " returning...");
                            return;
                        }
                        buff.put(new SimpleBufferableItem("t" + threadNumber + "i" + j));
                        assertTrue(buff.currentItems().size() <= 5);
                        Thread.sleep(20);
                    }
                } catch (AssertionFailedError | NoSuchElementException e) {
                    failed.set(true);
                    System.out.println("thread " + threadNumber + " failed");
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
            threads.add(t);
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }
        assertFalse(failed.get());
    }

    @Test
    public void test_Multithreading_Random() throws InterruptedException {
        FSFTBuffer<SimpleBufferableItem> buff = new FSFTBuffer<>(5, Duration.ofMillis(1));
        int threadCount = 20;
        int itemCount = 20;
        Set<Thread> threads = new HashSet<>();
        Random rand = new Random();
        AtomicBoolean failed = new AtomicBoolean(false);
        for (int i = 0; i < threadCount; i++) {
            int threadNumber = i;
            Thread t = new Thread(() -> {
                try {
                    for (int j = 0; j < itemCount; j++) {
                        if (failed.get()) {
                            System.out.println("thread " + threadNumber + " returning...");
                            return;
                        }
                        buff.put(new SimpleBufferableItem("t" + threadNumber + "i" + rand.nextInt(itemCount)));
                        assertTrue(buff.currentItems().size() <= 5);
                        Thread.sleep(20);
                    }
                } catch (AssertionFailedError | NoSuchElementException e) {
                    failed.set(true);
                    System.out.println("thread " + threadNumber + " failed");
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
            threads.add(t);
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }
        assertFalse(failed.get());
    }
}
