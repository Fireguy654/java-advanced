package info.kgeorgiy.ja.nebabin.hello;

import info.kgeorgiy.java.advanced.hello.HelloServer;
import info.kgeorgiy.java.advanced.hello.NewHelloServer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * A server, which answers its queries with {@code "Hello, <query information>}
 *
 * @author Nebabin Nikita
 */
public class HelloUDPServer implements NewHelloServer {
    private ExecutorService sender;
    private ExecutorService waiter;
    private final List<DatagramSocket> sockets = new ArrayList<>();
    boolean started = false;

    @Override
    public void start(int threads, Map<Integer, String> ports) {
        if (ports.isEmpty()) {
            return;
        }
        if (started) {
            throw new IllegalStateException("Trying to start started Server.");
        }
        started = true;
        sender = Executors.newFixedThreadPool(threads);
        waiter = Executors.newFixedThreadPool(ports.size());
        try {
            ports.forEach((port, ansForm) -> {
                try {
                    sockets.add(new DatagramSocket(port));
                } catch (SocketException e) {
                    throw new IllegalStateException("Can't create socket.", e);
                }
                waiter.submit(receiveJob(ansForm, sockets.getLast()));
            });
        } catch (IllegalStateException e) {
            close();
            throw e;
        }
    }

    private Runnable receiveJob(String ansForm, DatagramSocket socket) {
        return () -> {
            try {
                byte[] buffer = new byte[socket.getReceiveBufferSize()];
                DatagramPacket threadPacket = new DatagramPacket(buffer, buffer.length);
                while (!Thread.interrupted() && !socket.isClosed()) {
                    try {
                        socket.receive(threadPacket);
                        sender.submit(sendJob(
                                new String(threadPacket.getData(), threadPacket.getOffset(),
                                        threadPacket.getLength(), CHARSET),
                                threadPacket.getSocketAddress(), ansForm, socket
                        ));
                    } catch (SocketException e) {
                        break;
                    } catch (IOException | SecurityException ignored) {}
                }
            } catch (SocketException e) {
                throw new IllegalStateException("Can't use socket: " + e.getMessage(), e);
            }
        };
    }

    private Runnable sendJob(String query, SocketAddress from, String ansForm, DatagramSocket socket) {
        return () -> {
            try {
                byte[] resArray = (ansForm.replace("$", query)).getBytes(CHARSET);
                socket.send(new DatagramPacket(resArray, resArray.length, from));
            } catch (IOException | SecurityException ignored) {}
        };
    }

    @Override
    public void close() {
        if (started) {
            sockets.forEach(DatagramSocket::close);
            if (waiter != null) {
                waiter.close();
            }
            if (sender != null) {
                sender.close();
            }
            started = false;
        }
    }

    /**
     * Creates and starts {@link HelloUDPServer}.
     *
     * @param args server parameters in form: port threads
     */
    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Incorrect amount of parameters.");
        }
        try (HelloServer server = new HelloUDPServer()) {
            server.start(Integer.parseInt(args[0]), Integer.parseInt(args[1]));
        } catch (NumberFormatException e) {
            System.err.println("Parameters must be numbers.");
        } catch (IllegalStateException e) {
            System.err.println("Can't start: " + e.getMessage());
        }
    }

    private static final Charset CHARSET = StandardCharsets.UTF_8;
}
