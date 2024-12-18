package info.kgeorgiy.ja.nebabin.iterative;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * It is a monitor, which can be used as multi-thread queue of {@link Runnable} queries with maximal size.
 * It is thread-safe, so it can be used safely as a shared object.
 *
 * @author Nebabin Nikita
 */
public class QueryQueue {
    private final Queue<Runnable> queries;

    /**
     * Creates an instance of {@link QueryQueue}, which is an empty queue.
     */
    public QueryQueue() {
        queries = new ArrayDeque<>();
    }

    /**
     * Gets a query from queue.
     * It is a blocking operation: current thread is {@link QueryQueue#wait()} if queue is empty.
     *
     * @return a query.
     * @throws InterruptedException if current thread is interrupted during waiting.
     */
    public synchronized Runnable getQuery() throws InterruptedException {
        while (queries.isEmpty()) {
            wait();
        }
        notifyAll();
        return queries.poll();
    }

    /**
     * Puts a query in queue.
     * It is a blocking operation: current thread is {@link QueryQueue#wait()} if queue is full.
     *
     * @param query is a query to put.
     * @throws InterruptedException if current thread is interrupted during waiting.
     */
    public synchronized void addQuery(Runnable query) throws InterruptedException {
        while (queries.size() == MAXIMAL_AMOUNT) {
            wait();
        }
        notifyAll();
        queries.add(query);
    }

    private static final int MAXIMAL_AMOUNT = (1 << 10);
}
