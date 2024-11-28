package fsft.wikipedia;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class WikiMediatorTests {

    @Test
    public void test_PeakLoad() throws InterruptedException {
        WikiMediator wm = new WikiMediator();
        String[] searches = {"badminton", "java", "apple"};
        int[] sleepDurations = {100, 250, 400, 180, 310, 200};
        for (int i = 0; i < 3; i++) {
            List<String> results = wm.search(searches[i], 5);
            Thread.sleep(sleepDurations[2 * i]);
            wm.getPage(results.get(0));
            Thread.sleep(sleepDurations[2 * i + 1]);
        }
        int pl = wm.peakLoad(Duration.ofSeconds(1));
        List<Long> requestTimes = wm.getRequestTimes();
        for (int i = 0; i < requestTimes.size() - pl; i++) {
            assertTrue(requestTimes.get(i + pl) - requestTimes.get(i) >= 1000);
        }
    }

    @Test
    public void test_Zeitgeist() throws InterruptedException {
        WikiMediator wm = new WikiMediator();
        String[] searches = {"badminton", "java", "badminton", "badminton", "badminton", "apple", "java", "java"};
        int[] sleepDurations = {100, 250, 400, 180, 310, 200, 160, 290};
        long preAppleTime = 0;
        for (int i = 0; i < 8; i++) {
            if (i == 5) {
                preAppleTime = System.currentTimeMillis();
            }
            wm.search(searches[i], 5);
            Thread.sleep(sleepDurations[i]);
        }
        assertEquals(List.of("badminton", "java", "apple"), wm.zeitgeist(Duration.ofMillis(5000), 10));
        assertEquals(List.of("badminton", "java"), wm.zeitgeist(Duration.ofMillis(5000), 2));
        assertEquals(List.of("java", "apple"), wm.zeitgeist(Duration.ofMillis(System.currentTimeMillis() - preAppleTime), 10));
    }

    @Test
    public void test_CachingPages() throws InterruptedException {
        WikiMediator wm = new WikiMediator();
        long wikiGetStart = System.currentTimeMillis();
        wm.getPage("Canada");
        long wikiGetEnd = System.currentTimeMillis();
        Thread.sleep(Duration.ofMillis(1000));
        long cacheGetStart = System.currentTimeMillis();
        wm.getPage("Canada"); // second time retrieval from cache should be faster
        long cacheGetEnd = System.currentTimeMillis();
        assertTrue(cacheGetEnd - cacheGetStart < wikiGetEnd - wikiGetStart);
    }
}
