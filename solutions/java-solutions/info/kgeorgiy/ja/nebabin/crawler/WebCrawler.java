package info.kgeorgiy.ja.nebabin.crawler;

import info.kgeorgiy.java.advanced.crawler.*;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Crawls websites. Recursively downloads pages from websites.
 *
 * @author Nebabin Nikita.
 */
public class WebCrawler implements AdvancedCrawler {
    private static final int MAX_SIZE = 1 << 14;
    private static final Predicate<UrlWithHost> TRUE_FILTER = (url) -> true;
    private static final int NO_LIMIT = 100;
    private final Downloader downloader;
    private final ExecutorService loadService;
    private final ExecutorService extService;
    private final int perHost;
    private boolean closed;
    private final ConcurrentHashMap<String, HostQueue> hostSystem;

    /**
     * Creates an instance of {@link WebCrawler}.
     * Creates services for parallel download.
     *
     * @param downloader  is an object, by which the download of pages happens.
     * @param downloaders is the amount of threads, which will download pages.
     * @param extractors  is the amount of threads, which will extract links from pages.
     * @param perHost     is the amount of threads able to download from one host at one time.
     */
    public WebCrawler(final Downloader downloader, final int downloaders, final int extractors, final int perHost) {
        this.downloader = downloader;
        this.loadService = Executors.newFixedThreadPool(downloaders);
        this.extService = Executors.newFixedThreadPool(extractors);
        this.perHost = perHost;
        this.closed = false;
        this.hostSystem = new ConcurrentHashMap<>();
    }

    @Override
    public Result advancedDownload(final String url, final int depth, final List<String> hosts) {
        final Set<String> hostSet = hosts.stream().collect(Collectors.toSet());
        return filteredDownload(url, depth, (urlInfo) -> hostSet.contains(urlInfo.host));
    }

    @Override
    public Result download(final String url, final int depth, final Set<String> excludes) {
        return filteredDownload(url, depth, (urlInfo) -> excludes.stream().noneMatch(urlInfo.url::contains));
    }

    @Override
    public Result download(final String url, final int depth) {
        return filteredDownload(url, depth, TRUE_FILTER);
    }

    private Result filteredDownload(final String url, final int depth, final Predicate<UrlWithHost> filter) {
        if (closed) {
            throw new IllegalStateException("Trying to download with closed crawler.");
        }

        final Queue<UrlWithHost> urlsToDownload = new ArrayDeque<>();
        final Queue<UrlWithHost> layerExt = new ConcurrentLinkedQueue<>();
        final ConcurrentMap<String, Optional<IOException>> usedUrls = new ConcurrentHashMap<>();
        addUrlQuery(url, usedUrls, filter, layerExt);
        try {
            for (int i = 1; i <= depth; ++i) {
                urlsToDownload.addAll(layerExt);
                layerExt.clear();
                final CountDownLatch layerCnt = new CountDownLatch(urlsToDownload.size());
                final boolean lastLayer = i == depth;
                while (!urlsToDownload.isEmpty()) {
                    final UrlWithHost cur = urlsToDownload.poll();
                    hostSystem.compute(cur.host, (final String host, HostQueue curQueue) -> {
                        if (curQueue == null) {
                            curQueue = new HostQueue();
                        }
                        try {
                            curQueue.add(getLoadJob(cur, curQueue, lastLayer, usedUrls, layerExt, layerCnt, filter));
                        } catch (final InterruptedException e) {
                            throw new IllegalStateException("Was interrupted while working.");
                        }
                        return curQueue;
                    });
                }
                layerCnt.await();
            }
        } catch (final InterruptedException e) {
            throw new IllegalStateException("Was interrupted while working.");
        }
        final List<String> successfully = new ArrayList<>();
        final Map<String, IOException> excepted = new HashMap<>();
        usedUrls.forEach((final String workUrl, final Optional<IOException> res) -> {
            if (res.isPresent()) {
                excepted.put(workUrl, res.get());
            } else {
                successfully.add(workUrl);
            }
        });
        return new Result(successfully, excepted);
    }

    private Runnable getLoadJob(final UrlWithHost cur, final HostQueue curQueue, final boolean lastLayer, final Map<String, Optional<IOException>> usedUrls,
                                final Queue<UrlWithHost> layerExt, final CountDownLatch layerCnt, final Predicate<UrlWithHost> filter) {
        return () -> {
            try {
                final Document urlRes = downloader.download(cur.url);
                finishLoadJob(cur, curQueue);
                if (!lastLayer) {
                    extService.submit(getExtJob(urlRes, usedUrls, layerExt, layerCnt, filter));
                    return;
                }
            } catch (final IOException e) {
                finishLoadJob(cur, curQueue);
                usedUrls.put(cur.url, Optional.of(e));
            }
            layerCnt.countDown();
        };
    }

    private Runnable getExtJob(final Document urlRes, final Map<String, Optional<IOException>> usedUrls,
                               final Queue<UrlWithHost> layerExt, final CountDownLatch layerCnt, final Predicate<UrlWithHost> filter) {
        return () -> {
            try {
                final List<String> extUrls = urlRes.extractLinks();
                for (final String extUrl : extUrls) {
                    addUrlQuery(extUrl, usedUrls, filter, layerExt);
                }
            } catch (final IOException ignored) {
            } finally {
                layerCnt.countDown();
            }
        };
    }

    private void finishLoadJob(final UrlWithHost url, final HostQueue curQueue) {
        curQueue.finish();
        if (curQueue.isEmpty()) {
            hostSystem.compute(url.host, (final String host, final HostQueue resQueue) -> {
                if (resQueue != null && resQueue.isEmpty()) {
                    return null;
                }
                return resQueue;
            });
        }
    }

    private void addUrlQuery(
            final String url,
            final Map<String, Optional<IOException>> usedUrls,
            final Predicate<UrlWithHost> filter,
            final Queue<UrlWithHost> layerExt
    ) {
        final UrlWithHost curUrl = new UrlWithHost(url);
        if (filter.test(curUrl) && Objects.isNull(usedUrls.putIfAbsent(url, Optional.empty()))) {
            layerExt.add(curUrl);
        }
    }

    @Override
    public void close() {
        closed = true;
        loadService.close();
        extService.close();
    }

    static private String getHost(final String url) {
        try {
            return URLUtils.getHost(url);
        } catch (final MalformedURLException e) {
            throw new IllegalStateException("Given url is malformed.");
        }
    }

    private record UrlWithHost(String url, String host) {
        private UrlWithHost(final String url) {
            this(url, getHost(url));
        }
    }

    private class HostQueue {
        private int placeRemaining;
        private Queue<Runnable> hostUrls;

        private HostQueue() {
            placeRemaining = perHost;
            hostUrls = null;
        }

        private synchronized void add(final Runnable elem) throws InterruptedException {
            if (placeRemaining == 0) {
                if (hostUrls == null) {
                    hostUrls = new ArrayDeque<>();
                }
                while (hostUrls.size() == MAX_SIZE) {
                    wait();
                }
                hostUrls.add(elem);
            } else {
                --placeRemaining;
                loadService.submit(elem);
            }
        }

        private synchronized void finish() {
            if (hostUrls == null || hostUrls.isEmpty()) {
                ++placeRemaining;
            } else {
                loadService.submit(hostUrls.poll());
                notify();
            }
        }

        private boolean isEmpty() {
            return placeRemaining == perHost;
        }
    }

    /**
     * Crawls websites starting with given one using optionally given limitations.
     *
     * @param args consists of website and optional limitations with form: url [depth [downloads [extractors [perHost]]]]
     */
    public static void main(final String[] args) {
        if (args == null || args.length < 1 || args.length > 5) {
            System.err.println("Incorrect amount of params, correct form: " +
                    "WebCrawler url [depth [downloads [extractors [perHost]]]]");
            return;
        }
        final int[] borders = new int[4];
        for (int i = 0; i < 4; ++i) {
            if (i + 1 < args.length) {
                try {
                    borders[i] = Integer.parseInt(args[i + 1]);
                } catch (final NumberFormatException e) {
                    System.err.println(i + 2 + " argument must be integer: " + e.getMessage());
                    return;
                }
            } else {
                borders[i] = NO_LIMIT;
            }
        }
        try (final WebCrawler crawler =
                     new WebCrawler(new CachingDownloader(0), borders[1], borders[2], borders[3])) {
            crawler.download(args[0], borders[0]);
        } catch (final IOException e) {
            System.err.println("Can't create web crawler.");
        } catch (final IllegalStateException e) {
            System.err.println("Crawl error: " + e.getMessage());
        }
    }
}
