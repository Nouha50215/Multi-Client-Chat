package ma.aui.chat.server.model;

/**
 * Callback interface through which the server model reports what happens.
 *
 * The model only knows this interface, never JavaFX: any presentation layer
 * (JavaFX window, console, test harness) can implement it. This is what keeps
 * the Model independent of the View (Separation of Concerns).
 */
public interface ServerListener {

    /** An activity worth showing in the server log ("Server Started", "Welcome alice"...). */
    void onLog(String message);

    /** A client finished joining. {@code readOnly} is true when it gave no username. */
    void onUserJoined(String username, boolean readOnly);

    /** A client disconnected (typed end/bye, pressed LEAVE, or the connection dropped). */
    void onUserLeft(String username);
}
