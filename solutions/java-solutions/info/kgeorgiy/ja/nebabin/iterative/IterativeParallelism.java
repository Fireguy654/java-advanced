package info.kgeorgiy.ja.nebabin.iterative;

import info.kgeorgiy.ja.nebabin.iterative.shared.AdvancedIP;
import info.kgeorgiy.ja.nebabin.iterative.shared.ParallelMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Allows group of multi-thread operations with {@link List} of elements.
 * It implements {@link AdvancedIP} interface.
 *
 * @author Nebabin Nikita
 */
public class IterativeParallelism implements AdvancedIP {
    private final ParallelMapper executor;

    /**
     * Creates an instance of {@link IterativeParallelism}, which will create {@link Thread} in its methods.
     */
    public IterativeParallelism() {
        this.executor = null;
    }

    /**
     * Creates an instance of {@link IterativeParallelism}, which will use {@code executor} for parallel operations.
     *
     * @param executor is parallel mapper, which realises parallel operations.
     */
    public IterativeParallelism(ParallelMapper executor) {
        this.executor = executor;
    }

    @Override
    public String join(int threads, List<?> values, int step) throws InterruptedException {
        return parallelInvoke(threads, step, values, stream -> simpleJoin(stream.map(Object::toString)),
                IterativeParallelism::simpleJoin);
    }

    @Override
    public <T> List<T> filter(int threads, List<? extends T> values,
                              Predicate<? super T> predicate, int step) throws InterruptedException {
        return parallelInvoke(threads, step, values,
                stream -> stream.filter(predicate).toList().stream(),
                IterativeParallelism::simpleFlatMapping).collect(Collectors.<T>toList());
    }

    @Override
    public <T, U> List<U> map(int threads, List<? extends T> values,
                              Function<? super T, ? extends U> f, int step) throws InterruptedException {
        return parallelInvoke(threads, step, values, stream -> stream.map(f).toList().stream(),
                IterativeParallelism::simpleFlatMapping).collect(Collectors.<U>toList());
    }

    @Override
    public String join(int threads, List<?> values) throws InterruptedException {
        return join(threads, values, 1);
    }

    private static String simpleJoin(Stream<String> strs) {
        return strs.collect(Collectors.joining());
    }

    @Override
    public <T> List<T> filter(int threads, List<? extends T> values,
                              Predicate<? super T> predicate) throws InterruptedException {
        return filter(threads, values, predicate, 1);
    }

    @Override
    public <T, U> List<U> map(int threads, List<? extends T> values,
                              Function<? super T, ? extends U> f) throws InterruptedException {
        return map(threads, values, f, 1);
    }

    @Override
    public <T> T reduce(int threads, List<T> values, T identity, BinaryOperator<T> operator,
                        int step) throws InterruptedException {
        Function<Stream<T>, T> reducer = stream -> stream.reduce(identity, operator);
        return parallelInvoke(threads, step, values, reducer, reducer);
    }

    @Override
    public <T, R> R mapReduce(int threads, List<T> values, Function<T, R> lift, R identity, BinaryOperator<R> operator,
                              int step) throws InterruptedException {
        Function<Stream<R>, R> reducer = stream -> stream.reduce(identity, operator);
        return parallelInvoke(threads, step, values,
                reducer.compose(stream -> stream.map(lift)), reducer);
    }

    private static <T> Stream<T> simpleFlatMapping(Stream<Stream<T>> streams) {
        return streams.flatMap(Function.identity());
    }

    @Override
    public <T> T maximum(int threads, List<? extends T> values,
                         Comparator<? super T> comparator, int step) throws InterruptedException {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Trying to get element from empty list");
        }
        Function<Stream<? extends T>, T> maximize = stream -> stream.max(comparator).get();
        return parallelInvoke(threads, step, values, maximize, maximize);
    }

    @Override
    public <T> T minimum(int threads, List<? extends T> values,
                         Comparator<? super T> comparator, int step) throws InterruptedException {
        return maximum(threads, values, comparator.reversed(), step);
    }

    @Override
    public <T> boolean all(int threads, List<? extends T> values,
                           Predicate<? super T> predicate, int step) throws InterruptedException {
        return parallelInvoke(threads, step, values, vals -> vals.allMatch(predicate), IterativeParallelism::andStream);
    }

    @Override
    public <T> boolean any(int threads, List<? extends T> values,
                           Predicate<? super T> predicate, int step) throws InterruptedException {
        return !all(threads, values, predicate.negate(), step);
    }

    @Override
    public <T> int count(int threads, List<? extends T> values,
                         Predicate<? super T> predicate, int step) throws InterruptedException {
        return parallelInvoke(threads, step, values, vals -> vals.filter(predicate).count(),
                IterativeParallelism::sumUp).intValue();
    }

    @Override
    public <T> T maximum(int threads, List<? extends T> values,
                         Comparator<? super T> comparator) throws InterruptedException {
        return maximum(threads, values, comparator, 1);
    }

    @Override
    public <T> T minimum(int threads, List<? extends T> values,
                         Comparator<? super T> comparator) throws InterruptedException {
        return maximum(threads, values, comparator.reversed());
    }

    @Override
    public <T> boolean all(int threads, List<? extends T> values,
                           Predicate<? super T> predicate) throws InterruptedException {
        return all(threads, values, predicate, 1);
    }

    private static boolean andStream(Stream<Boolean> bools) {
        return bools.allMatch(bool -> bool);
    }

    @Override
    public <T> boolean any(int threads, List<? extends T> values,
                           Predicate<? super T> predicate) throws InterruptedException {
        return !all(threads, values, predicate.negate());
    }

    @Override
    public <T> int count(int threads, List<? extends T> values,
                         Predicate<? super T> predicate) throws InterruptedException {
        return count(threads, values, predicate, 1);
    }

    private static long sumUp(Stream<Long> elems) {
        return elems.reduce(0L, Long::sum);
    }

    private static void checkThreads(int threads) {
        if (threads <= 0) {
            throw new IllegalArgumentException("Incorrect amount of threads.");
        }
    }

    private <T, A, R> R parallelInvoke(int threads, int step,
                                           List<T> values,
                                           Function<? super Stream<T>, ? extends A> mapper,
                                           Function<? super Stream<A>, ? extends R> finisher) throws InterruptedException {
        checkThreads(threads);
        int size = values.size() / step + (values.size() % step == 0 ? 0 : 1);
        int threadAmount = size / threads;
        int threadDif = size % threads;
        List<A> results;
        List<Stream<T>> portions = IntStream.range(0, Integer.min(size, threads))
                .mapToObj(
                        portionInd -> IntStream.range(
                                threadAmount * portionInd + Integer.min(portionInd, threadDif),
                                threadAmount * (portionInd + 1) + Integer.min(portionInd + 1, threadDif)
                        ).mapToObj(stepInd -> values.get(stepInd * step))
                ).toList();
        if (executor == null) {
            results = new ArrayList<>();
            List<Thread> thrs = new ArrayList<>();
            for (int i = 0; i < Integer.min(size, threads); ++i) {
                Thread thread = new Thread(getThreadRunnable(i, portions.get(i), mapper, results));
                results.add(null);
                thread.start();
                thrs.add(thread);
            }
            for (int i = 0; i < thrs.size(); ++i) {
                try {
                    thrs.get(i).join();
                } catch (InterruptedException e) {
                    for (int j = i; j < thrs.size(); ++j) {
                        thrs.get(j).interrupt();
                    }
                    for (int j = i; j < thrs.size(); ++j) {
                        try {
                            thrs.get(j).join();
                        } catch (InterruptedException oth_e) {
                            --j;
                            e.addSuppressed(oth_e);
                        }
                    }
                    throw e;
                }
            }
        } else {
            results = executor.map(mapper, portions);
        }
        return finisher.apply(results.stream());
    }

    private static <T, R> Runnable getThreadRunnable(int index, Stream<T> values,
                                                     Function<? super Stream<T>, ? extends R> mapper,
                                                     List<R> results) {
        return () -> results.set(index, mapper.apply(values));
    }
}
