package ma.aui.chat.server.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Serves one connected client on its own virtual thread.
 *
 * Text protocol (one line per message, UTF-8):
 *   client -> server  first line      : username (empty = READ-ONLY MODE)
 *   client -> server  "allUsers"      : server replies with the active users, to this client only
 *   client -> server  "end" / "bye"   : leave the chat
 *   client -> server  anything else   : chat message, broadcast as "[user HH:mm] text"
 *   server -> client  any line        : text to display ("SYSTEM: ..." and "*** ... ***" are notices)
 */
public class ClientHandler implements Runnable {

    private final Socket socket;
    private final ChatServer server;
    private PrintWriter out;
    private volatile String username;
    private volatile boolean readOnly;

    ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try (socket;
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

            synchronized (this) {
                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            }

            String requestedName = in.readLine();
            if (requestedName == null) {
                return; // client vanished before identifying itself
            }
            server.register(this, requestedName);

            String line;
            while ((line = in.readLine()) != null) {
                String message = line.strip();
                if (message.isEmpty()) {
                    continue;
                }
                if (message.equalsIgnoreCase("end") || message.equalsIgnoreCase("bye")) {
                    send("SYSTEM: Goodbye " + username + "!");
                    break;
                }
                if (message.equals("allUsers")) {
                    send(server.activeUsersMessage());
                    continue;
                }
                if (readOnly) {
                    send("SYSTEM: You are in READ-ONLY MODE and cannot send messages.");
                    continue;
                }
                server.broadcast(server.format(username, message));
            }
        } catch (IOException e) {
            // Connection dropped (window closed, network lost): treated like a normal leave.
        } finally {
            server.remove(this);
        }
    }

    /** Thread-safe write of one line to this client. */
    synchronized void send(String line) {
        if (out != null) {
            out.println(line);
        }
    }

    void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // nothing else to do
        }
    }

    void setIdentity(String username, boolean readOnly) {
        this.username = username;
        this.readOnly = readOnly;
    }

    String getUsername() {
        return username;
    }

    boolean isReadOnly() {
        return readOnly;
    }
}
