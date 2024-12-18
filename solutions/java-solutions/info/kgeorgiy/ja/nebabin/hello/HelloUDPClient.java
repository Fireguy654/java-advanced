package info.kgeorgiy.ja.nebabin.hello;

import info.kgeorgiy.java.advanced.hello.HelloClient;

import java.io.IOException;
import java.net.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * It is a server client, which sends queries with exact amount of threads, cnts and format.
 *
 * @author Nebabin Nikita
 */
public class HelloUDPClient implements HelloClient {
    @Override
    public void run(String host, int port, String prefix, int threads, int requests) {
        InetAddress hostAddr;
        try {
            hostAddr = InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            System.err.println("Host is unknown: " + e.getMessage());
            return;
        }
        try (ExecutorService sender = Executors.newFixedThreadPool(threads)) {
            for (int i = 1; i <= threads; ++i) {
                sender.submit(job(prefix, i, hostAddr, port, requests));
            }
        }
    }

    private Runnable job(String prefix, int ind, InetAddress hostAddr, int port, int requests) {
        return () -> {
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setSoTimeout(TIMEOUT);
                String ans;
                DatagramPacket mes = new DatagramPacket(new byte[0], 0, hostAddr, port);
                byte[] resArray = new byte[socket.getReceiveBufferSize()];
                DatagramPacket res = new DatagramPacket(resArray, resArray.length);
                for (int j = 1; j <= requests; ++j) {
                    String query = prefix + ind + "_" + j;
                    while (true) {
                        try {
                            mes.setData(query.getBytes(CHARSET));
                            socket.send(mes);
                            socket.receive(res);
                            ans = new String(res.getData(), res.getOffset(), res.getLength(), CHARSET);
                            if (correct(ans, ind, j)) {
                                System.out.println(query);
                                System.out.println(ans);
                                break;
                            }
                        } catch (IOException | SecurityException ignored) {}
                    }
                }
            } catch (SocketException e) {
                System.err.println("Thread " + ind + " can't create a socket: " + e.getMessage());
            }
        };
    }

    private static boolean correct(String ans, int threadInd, int queryInd) {
        Matcher matcher = INTEGER_PATTERN.matcher(ans);
        return expectMatch(matcher, threadInd) && expectMatch(matcher, queryInd) && !matcher.find();
    }

    private static boolean expectMatch(Matcher matcher, int num) {
        try {
            return matcher.find() && Integer.parseInt(matcher.group()) == num;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Creates an {@link HelloUDPServer} and runs it with given parameters.
     *
     * @param args parameters of run in form: host port queryPrefix threads requests.
     */
    public static void main(String[] args) {
        if (args.length != 5) {
            System.out.println("Incorrect amount of arguments.");
        }
        HelloClient client = new HelloUDPClient();
        try {
            client.run(args[0], Integer.parseInt(args[1]), args[2], Integer.parseInt(args[3]), Integer.parseInt(args[4]));
        } catch (NumberFormatException e) {
            System.err.println("Second, fourth and fifth parameters must be numbers: " + e.getMessage());
        }
    }

    private static final Pattern INTEGER_PATTERN = Pattern.compile("\\d+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Charset CHARSET = StandardCharsets.UTF_8;
    private static final int TIMEOUT = 100;
}
