package fsft.wikipedia;

import fsft.fsftbuffer.FSFTBuffer;
import io.github.fastily.jwiki.core.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * A mediator service for accessing Wikipedia pages and analyzing user request statistics.
 *
 * <p>Main methods provided:
 * <ul>
 *   <li>{@link #search(String, int)}: Searches Wikipedia for page titles matching a query
 *   and returns up to a given limit of results</li>
 *   <li>{@link #getPage(String)}: Retrieves the content of a Wikipedia page for a given
 *   title</li>
 *   <li>{@link #zeitgeist(Duration, int)}: Analyzes recent search and page access requests
 *   to determine the most frequent queries within the most recent time window of a specified
 *   duration</li>
 *   <li>{@link #peakLoad(Duration)}: Computes the peak number of requests within any time
 *   window of a specified duration</li>
 *   <li>{@link #getRequestTimes()}: Returns a list of request timestamps relative to the
 *   instantiation time of the mediator.</li>
 * </ul>
 *
 * <p>Notes:
 * <ul>
 *   <li>{@code WikiMediator} uses JWiki API to fetch and search for content from Wikipedia</li>
 *   <li>{@code WikiMediator} is thread-safe and can be called concurrently from multiple
 *   threads</li>
 *   <li>The notion of an "request" used for statistical analysis includes any call to any
 *   method in the {@code WikiMediator} class</li>
 *   <li>A method call that is passed an invalid argument (like null) <b>does not</b> count
 *   as a request and this occurrence is not considered in any statistical analyses</li>
 *   <li>The default constructor initializes the mediator for the English-language Wikipedia
 *   domain. Additional constructors allow for specifying the domain.</li>
 * </ul>
 */
public class WikiMediator {

    // Abstraction function is
    //      AF(r) = a mediator service for interacting with Wikipedia, where:
    //      - r.wiki is the Wikipedia API interface
    //      - r.pageCache contains cached Wikipedia pages to minimize network accesses
    //      - r.stringRequests is a map where the values are strings representing search
    //          terms or page titles, and the key of each value is the timestamp at which
    //          that string was queried
    //      - r.requestTimes is a list of all timestamps marking the times of all requests
    //          (method calls) to this instance of WikiMediator

    // Rep invariant is
    //      - wiki is not null
    //      - pageCache is not null and does not contain null elements
    //      - stringRequests is not null and does not contain null keys or values
    //      - getRequestTimes is not null and does not contain null elements

    private final long t0;
    private final Wiki wiki;
    private final FSFTBuffer<WikiPage> pageCache;
    private final List<WikiMediatorRequest> requestHistory;

    /* TODO: Implement this datatype

        You must implement the methods with the exact signatures
        as provided in the statement for this mini-project.

        You must add method signatures even for the methods that you
        do not plan to implement. You should provide skeleton implementation
        for those methods, and the skeleton implementation could return
        values like null.

        You can add constructors that could help with your implementation of
        WikiMediatorServer but there should be a default constructor that takes
        no arguments for Task 3.

     */

    /**
     * Creates a new instance of {@code WikiMediator} using the default Wikipedia domain
     * ("en.wikipedia.org").
     */
    public WikiMediator() {
        this("en.wikipedia.org");
    }

    /**
     * Creates a new instance of {@code WikiMediator} for the specified Wikipedia domain.
     *
     * @param domain the Wikipedia domain to use for page fetching and search, is not null
     */
    public WikiMediator(String domain) {
        if (domain == null) {
            throw new IllegalArgumentException();
        }
        t0 = System.currentTimeMillis();
        wiki = new Wiki.Builder().withDomain(domain).build();
        pageCache = new FSFTBuffer<>(); // use default buffer settings??
        requestHistory = new CopyOnWriteArrayList<>();
    }

    /**
     * Searches Wikipedia for page titles matching the given search term, and returns
     * up to a given limit of pages.
     *
     * @param searchTerm the term to search for, is not null
     * @param limit the maximum number of results to return
     * @return a list of up to {@code limit} page titles that match the search term;
     *          if {@code limit} <= 0, returns an empty list
     */
    public List<String> search(String searchTerm, int limit) {
        if (searchTerm == null) {
            throw new IllegalArgumentException();
        }
        requestHistory.add(new WikiMediatorRequest(searchTerm));
        if (limit <= 0) {
            return new ArrayList<>();
        }
        return wiki.search(searchTerm, limit);
    }

    /**
     * Retrieves the content of a Wikipedia page for a given title.
     *
     * @param pageTitle the title of the page to retrieve; must not be null
     * @return the text content of the Wikipedia page corresponding to {@code pageTitle}
     */
    public String getPage(String pageTitle) {
        if (pageTitle == null) {
            throw new IllegalArgumentException();
        }
        requestHistory.add(new WikiMediatorRequest(pageTitle));
        if (!pageCache.touch(pageTitle)) {
            pageCache.put(new WikiPage(wiki, pageTitle));
        }
        return pageCache.get(pageTitle).getText();
    }

    /**
     * Returns the most common search terms or page titles requested in the most recent time
     * window of specified length.
     *
     * @param duration the length of the time window to consider, is not null and is a positive
     *                 duration of time
     * @param limit the maximum number of items to return
     * @return a list of the most common search terms or page titles requested in the specified
     *         duration, sorted in non-increasing order of request frequency (only considering
     *         requests made during the time window). If multiple items were requested the same
     *         number of times, ties are broken arbitrarily. Returns an empty list if limit
     *         <= 0.
     */
    public List<String> zeitgeist(Duration duration, int limit) {
        if (duration == null) {
            throw new IllegalArgumentException();
        }
        long now = System.currentTimeMillis();
        requestHistory.add(new WikiMediatorRequest());
        if (limit <= 0) {
            return new ArrayList<>();
        }
        synchronized (requestHistory) {
            return requestHistory.stream()
                    .filter(r -> !r.getString().isEmpty())
                    .filter(r -> now - r.getTime() <= duration.toMillis())
                    .collect(Collectors.groupingBy(WikiMediatorRequest::getString, Collectors.counting()))
                    .entrySet().stream()
                    .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                    .map(Map.Entry::getKey)
                    .limit(limit)
                    .toList();
        }
    }

    /**
     * Returns the maximum number of requests made in <b>any</b> time window of a given duration.
     *
     * @param duration the length of the time window to consider, is not null and is a positive
     *                 duration of time
     * @return the maximum number of requests made in any time window of length {@code duration}
     */
    public int peakLoad(Duration duration) {
        if (duration == null) {
            throw new IllegalArgumentException();
        }
        requestHistory.add(new WikiMediatorRequest());
        int max = 1;
        int start = 0;
        int end = 1;
        long delta = duration.toMillis();
        while (end < requestHistory.size()) {
            if (requestHistory.get(end).getTime() - requestHistory.get(start).getTime() >= delta) {
                start++;
            } else {
                max = end - start + 1;
            }
            end++;
        }
        return max;
    }

    public List<Long> getRequestTimes() {
        synchronized (requestHistory) {
            return requestHistory.stream().map(t -> t.getTime() - t0).toList();
        }
    }
}
