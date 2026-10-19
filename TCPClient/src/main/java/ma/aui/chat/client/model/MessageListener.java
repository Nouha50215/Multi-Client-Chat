package ma.aui.chat.client.model;

/**
 * How the client model reports incoming data. Implemented by the view (or a test),
 * so the model never depends on JavaFX.
 */
public interface MessageListener {

    /** A line arrived from the server (chat message or notice). */
    void onMessage(String line);

    /** The connection is closed (the user left, or the server went away). Called once. */
    void onDisconnected();
}
