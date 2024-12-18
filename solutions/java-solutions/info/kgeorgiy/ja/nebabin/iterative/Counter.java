package info.kgeorgiy.ja.nebabin.iterative;

/**
 * It is a monitor, which can be used as multi-thread counter dropping from fixed size to zero and saves given exceptions.
 * It is thread-safe, so it can be used safely as a shared object.
 *
 * @author Nebabin Nikita
 */
public class Counter {
    private int cnt;
    private RuntimeException exception = null;

    /**
     * Creates an instance of {@link Counter} with fixed {@code cnt}
     *
     * @param cnt is size of counter.
     */
    public Counter(int cnt) {
        this.cnt = cnt;
    }

    /**
     * Decreases the counter and notifies waiting object if count equals to zero.
     */
    public synchronized void decrease() {
        --cnt;
        if (cnt == 0) {
            notify();
        }
    }

    /**
     * Returns true if and only if any exceptions were saved.
     *
     * @return boolean value showing if any exceptions were saved.
     */
    public synchronized boolean hasException() {
        return exception != null;
    }

    /**
     * Returns saved exception if any were saved and null otherwise.
     *
     * @return saved exception.
     */
    public synchronized RuntimeException getException() {
        return exception;
    }

    /**
     * Adds an exception to this counter.
     * If any exceptions were added before it adds {@code e} as suppressed to the current saved exception.
     *
     * @param e is an exception to add.
     */
    public synchronized void addException(RuntimeException e) {
        if (exception == null) {
            exception = e;
        } else {
            exception.addSuppressed(e);
        }
    }

    /**
     * Current thread waits till this counter drops to zero.
     *
     * @throws InterruptedException if current thread was interrupted during waiting.
     */
    public synchronized void waitZero() throws InterruptedException {
        while (cnt != 0) {
            wait();
        }
    }
}
