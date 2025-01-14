package info.kgeorgiy.ja.nebabin.iterative;

import info.kgeorgiy.ja.nebabin.iterative.shared.ParallelMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Allows parallel map operation for {@link List} with fixed amount of threads for an instance.
 * It is thread-safe class, so it can be used as a shared object.
 * It implements {@link ParallelMapper}.
 *
 * @author Nebabin Nikita
 */
public class ParallelMapperImpl implements ParallelMapper {
    private final QueryQueue queue;
    private final List<Thread> threads;

    /**
     * Creates an instance of {@link ParallelMapperImpl} with fixed amount of {@code threads}.
     *
     * @param threads is the amount of threads, which this instance can use.
     */
    public ParallelMapperImpl(int threads) {
        this.queue = new QueryQueue();
        this.threads = new ArrayList<>(threads);
        for (int i = 0; i < threads; ++i) {
            // :NOTE: move to var
            this.threads.add(new Thread(this::threadJob));
            this.threads.get(i).start();
        }
    }

    @Override
    public <T, R> List<R> map(Function<? super T, ? extends R> f, List<? extends T> args) throws InterruptedException {
        List<R> res = new ArrayList<>(args.size());
        Counter counter = new Counter(args.size());
        for (int i = 0; i < args.size(); ++i) {
            res.add(null);
            queue.addQuery(applyParallelFunction(args, f, res, i, counter));
        }
        counter.waitZero();
        if (counter.hasException()) {
            throw counter.getException();
        }
        return res;
    }

    @Override
    public synchronized void close() {
        threads.forEach(Thread::interrupt);
        for (int i = 0; i < threads.size(); ++i) {
            try {
                threads.get(i).join();
            } catch (InterruptedException e) {
                --i;
            }
        }
        threads.clear();
    }

    private static <T, R> Runnable applyParallelFunction(List<? extends T> args, Function<? super T, ? extends R> f, List<R> res, int index, Counter counter) {
        return () -> {
            try {
                res.set(index, f.apply(args.get(index)));
            } catch (RuntimeException e) {
                counter.addException(e);
            }
            counter.decrease();
        };
    }

    private void threadJob() {
        while (!Thread.interrupted()) {
            try {
                queue.getQuery().run();
            } catch (InterruptedException e) {
                return;
            }
        }
    }
}
