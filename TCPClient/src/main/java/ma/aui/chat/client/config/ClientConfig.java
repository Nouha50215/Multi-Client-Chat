package ma.aui.chat.client.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

/**
 * Server address the client connects to.
 *
 * Priority:
 *   1. command-line arguments  <ServerIPAddress> <PortNumber>
 *   2. ./config.properties     (file next to the JAR / working directory)
 *   3. config.properties       (built-in default inside the JAR)
 */
public record ClientConfig(String host, int port) {

    public static final String FILE_NAME = "config.properties";

    public static ClientConfig resolve(List<String> args) {
        ClientConfig fromFile = loadFile();
        if (args != null && args.size() >= 2) {
            try {
                return new ClientConfig(args.get(0).trim(), Integer.parseInt(args.get(1).trim()));
            } catch (NumberFormatException e) {
                System.err.println("Invalid port '" + args.get(1) + "', using " + FILE_NAME);
            }
        }
        return fromFile;
    }

    private static ClientConfig loadFile() {
        Properties props = new Properties();
        Path external = Path.of(FILE_NAME);
        try {
            if (Files.isRegularFile(external)) {
                try (Reader reader = Files.newBufferedReader(external, StandardCharsets.UTF_8)) {
                    props.load(reader);
                }
            } else {
                try (InputStream in = ClientConfig.class.getResourceAsStream("/" + FILE_NAME)) {
                    if (in != null) {
                        props.load(in);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Could not read " + FILE_NAME + ", using defaults: " + e.getMessage());
        }
        String host = props.getProperty("server.host", "localhost").trim();
        int port = Integer.parseInt(props.getProperty("server.port", "3000").trim());
        return new ClientConfig(host, port);
    }
}
