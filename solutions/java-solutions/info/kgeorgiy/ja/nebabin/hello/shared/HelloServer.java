package info.kgeorgiy.ja.nebabin.hello.shared;

/**
 * Test interface for HelloServerTest.
 *
 * @author Georgiy Korneev (kgeorgiy@kgeorgiy.info)
 */
public interface HelloServer extends AutoCloseable {
    /**
     * Starts a new Hello server.
     * This method should return immediately.
     *
     * @param port server port.
     * @param threads number of working threads.
     */
    void start(int port, int threads);

    /**
     * Stops server and deallocates all resources.
     */
    @Override
    void close();
}
