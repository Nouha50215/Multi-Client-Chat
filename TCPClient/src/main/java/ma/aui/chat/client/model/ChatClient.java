package ma.aui.chat.client.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Client-side model: owns the TCP socket, sends lines and receives lines
 * on a background virtual thread. Contains no JavaFX code.
 */
public class ChatClient {

    private static final int CONNECT_TIMEOUT_MS = 5000;

    private final String host;
    private final int port;
    private final MessageListener listener;

    private Socket socket;
    private PrintWriter out;
    private String username = "";
    private boolean readOnly;
    private final AtomicBoolean connected = new AtomicBoolean(false);

    public ChatClient(String host, int port, MessageListener listener) {
        this.host = host;
        this.port = port;
        this.listener = Objects.requireNonNull(listener);
    }

    /**
     * Connects and identifies. An empty username means READ-ONLY MODE.
     * @throws IOException if the server cannot be reached
     */
    public void connect(String username) throws IOException {
        this.username = username == null ? "" : username.strip();
        this.readOnly = this.username.isEmpty();

        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

        out.println(this.username); // first line of the protocol = username
        connected.set(true);

        Thread.ofVirtual().name("chat-receiver").start(() -> receiveLoop(in));
    }

    private void receiveLoop(BufferedReader in) {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                listener.onMessage(line);
            }
        } catch (IOException e) {
            // socket closed by us (leave) or by the server
        } finally {
            closeSocket();
            if (connected.getAndSet(false)) {
                listener.onDisconnected();
            }
        }
    }

    /**
     * Sends what the user typed. "end"/"bye" disconnect; "allUsers" asks the server for the user list.
     * In READ-ONLY MODE only commands are allowed.
     */
    public void send(String text) {
        if (!isConnected() || text == null) {
            return;
        }
        String message = text.strip();
        if (message.isEmpty()) {
            return;
        }
        if (isLeaveCommand(message)) {
            disconnect();
            return;
        }
        if (readOnly && !message.equals("allUsers")) {
            listener.onMessage("SYSTEM: READ-ONLY MODE - you cannot send messages. Restart and enter a username to chat.");
            return;
        }
        out.println(message);
    }

    /** Tells the server we are leaving, then closes the connection. */
    public void disconnect() {
        if (!isConnected()) {
            return;
        }
        out.println("bye");      // notify the server
        out.flush();
        closeSocket();           // the receive loop ends and fires onDisconnected
    }

    public static boolean isLeaveCommand(String message) {
        return message.equalsIgnoreCase("end") || message.equalsIgnoreCase("bye");
    }

    private void closeSocket() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
            // already closed
        }
    }

    public boolean isConnected() {
        return connected.get();
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public String getUsername() {
        return username;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }
}
