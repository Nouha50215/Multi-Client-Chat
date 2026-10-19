import javafx.application.Application;
import ma.aui.chat.server.view.ServerApp;

/**
 * Entry point:  java TCPServer   (or  java -jar TCPServer.jar)
 *
 * The port comes from config.properties, not from the command line.
 * This launcher does not extend Application on purpose: that lets the
 * shaded (fat) JAR start JavaFX from the classpath without extra JVM options.
 */
public class TCPServer {

    public static void main(String[] args) {
        Application.launch(ServerApp.class, args);
    }
}
