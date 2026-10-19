package ma.aui.chat.server.model;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The central chat server: accepts connections and distributes messages.
 *
 * Concurrency model: the accept loop runs on one virtual thread, and every
 * connected client gets its own virtual thread ({@link ClientHandler}).
 * Shared state lives in concurrent collections so handlers can run in parallel.
 *
 * This class has no JavaFX dependency; it reports events through {@link ServerListener}.
 */
public class ChatServer {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final int port;
    private final ServerListener listener;

    /** Every live connection (named users and read-only guests): all receive broadcasts. */
    private final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();
    /** Display name -> handler, used for allUsers and unique names. */
    private final Map<String, ClientHandler> users = new ConcurrentHashMap<>();
    private final AtomicInteger guestCounter = new AtomicInteger();

    private volatile ServerSocket serverSocket;
    private volatile boolean running;

    public ChatServer(int port, ServerListener listener) {
        this.port = port;
        this.listener = Objects.requireNonNull(listener);
    }

    /** Opens the server socket and starts accepting clients in the background. */
    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        listener.onLog("Server Started on port " + port);
        Thread.ofVirtual().name("chat-acceptor").start(this::acceptLoop);
    }

    private void acceptLoop() {
        while (running) {
            listener.onLog("Waiting for Client...");
            try {
                Socket socket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(socket, this);
                clients.add(handler);
                listener.onLog("Connection from " + socket.getInetAddress().getHostAddress()
                        + ":" + socket.getPort());
                // One lightweight virtual thread per client.
                Thread.ofVirtual().name("client-" + socket.getPort()).start(handler);
            } catch (IOException e) {
                if (running) {
                    listener.onLog("Accept failed: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Called by a handler once it has read the requested username (first line).
     * An empty name joins in READ-ONLY MODE as "Guest-n". Duplicate names get a suffix.
     */
    synchronized void register(ClientHandler handler, String requestedName) {
        String name;
        boolean readOnly = requestedName == null || requestedName.isBlank();
        if (readOnly) {
            name = "Guest-" + guestCounter.incrementAndGet();
        } else {
            name = requestedName.strip();
            String base = name;
            int n = 2;
            while (users.containsKey(name)) {
                name = base + "(" + n++ + ")";
            }
        }
        handler.setIdentity(name, readOnly);
        users.put(name, handler);

        if (readOnly) {
            handler.send("SYSTEM: Connected in READ-ONLY MODE as " + name
                    + ". You can read messages and type allUsers, but not send messages.");
            listener.onLog("Welcome " + name + " (read-only)");
        } else {
            handler.send("SYSTEM: Connected as " + name + ". Type allUsers to see who is online, end or bye to leave.");
            listener.onLog("Welcome " + name);
        }
        listener.onUserJoined(name, readOnly);
        broadcast("*** " + name + (readOnly ? " joined in read-only mode" : " joined the chat") + " ***");
    }

    /** Formats a chat line with the sender's username and the current time. */
    String format(String username, String text) {
        return "[" + username + " " + LocalTime.now().format(TIME) + "] " + text;
    }

    /** Sends a line to every active connection. */
    void broadcast(String line) {
        for (ClientHandler client : clients) {
            client.send(line);
        }
    }

    /** The reply to the allUsers command (sent only to the asking client). */
    String activeUsersMessage() {
        List<String> names = new ArrayList<>();
        users.forEach((name, handler) -> names.add(handler.isReadOnly() ? name + " (read-only)" : name));
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return "SYSTEM: Active users (" + names.size() + "): " + String.join(", ", names);
    }

    /** Called by a handler when its connection ends, for whatever reason. */
    void remove(ClientHandler handler) {
        if (!clients.remove(handler)) {
            return; // already removed
        }
        String name = handler.getUsername();
        if (name != null && users.remove(name, handler)) {
            listener.onLog(name + " left the chat");
            listener.onUserLeft(name);
            broadcast("*** " + name + " left the chat ***");
        }
    }

    /** Stops accepting clients and closes every connection. */
    public void stop() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
            // shutting down anyway
        }
        for (ClientHandler client : clients) {
            client.close();
        }
        listener.onLog("Server stopped");
    }

    public int getPort() {
        return port;
    }
}
