import javafx.application.Application;
import ma.aui.chat.client.view.ClientApp;

/**
 * Entry point:  java TCPClient <ServerIPAddress> <PortNumber>
 *        e.g.   java TCPClient localhost 3000
 *        or     java -jar TCPClient.jar localhost 3000
 *
 * Without arguments, the address comes from config.properties.
 * This launcher does not extend Application on purpose, so the fat JAR runs
 * JavaFX from the classpath without extra JVM options.
 */
public class TCPClient {

    public static void main(String[] args) {
        if (args.length != 0 && args.length != 2) {
            System.err.println("Usage: java TCPClient <ServerIPAddress> <PortNumber>");
            System.exit(1);
        }
        Application.launch(ClientApp.class, args);
    }
}
