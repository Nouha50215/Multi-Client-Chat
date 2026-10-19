package ma.aui.chat.server.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Network configuration (IP address + port) loaded at runtime from a standard
 * {@code .properties} file, so the deployment can change without recompiling.
 *
 * Lookup order:
 *   1. ./config.properties  (file next to the JAR / current working directory)
 *   2. config.properties    (built-in default inside the JAR, src/main/resources)
 */
public record ServerConfig(String host, int port) {

    public static final String FILE_NAME = "config.properties";

    public static ServerConfig load() {
        Properties props = new Properties();
        Path external = Path.of(FILE_NAME);
        try {
            if (Files.isRegularFile(external)) {
                try (Reader reader = Files.newBufferedReader(external, StandardCharsets.UTF_8)) {
                    props.load(reader);
                }
            } else {
                try (InputStream in = ServerConfig.class.getResourceAsStream("/" + FILE_NAME)) {
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
        return new ServerConfig(host, port);
    }
}
