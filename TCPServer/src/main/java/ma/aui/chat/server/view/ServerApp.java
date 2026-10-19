package ma.aui.chat.server.view;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import ma.aui.chat.server.config.ServerConfig;
import ma.aui.chat.server.model.ChatServer;
import ma.aui.chat.server.model.ServerListener;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JavaFX view of the server: an activity log and a live list of connected users.
 *
 * It only talks to the model through ChatServer's public API and receives events
 * through the ServerListener interface. Model callbacks arrive on background
 * (virtual) threads, so every UI change is wrapped in Platform.runLater.
 */
public class ServerApp extends Application implements ServerListener {

    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final TextArea logArea = new TextArea();
    private final ObservableList<String> users = FXCollections.observableArrayList();
    private final Map<String, String> userColors = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private final Label usersTitle = new Label("Connected users (0)");
    private final Circle statusDot = new Circle(6);
    private final Label statusLabel = new Label("Starting...");

    private ChatServer server;

    @Override
    public void start(Stage stage) {
        ServerConfig config = ServerConfig.load();

        stage.setTitle("Chat Server");
        stage.setScene(buildScene(config));
        stage.show();

        server = new ChatServer(config.port(), this);
        try {
            server.start();
            setStatus(true, "Online · listening on port " + config.port());
        } catch (IOException e) {
            onLog("ERROR: cannot start server on port " + config.port() + " (" + e.getMessage() + ")");
            setStatus(false, "Offline · port " + config.port() + " unavailable");
        }
    }

    private Scene buildScene(ServerConfig config) {
        GridPane root = new GridPane();
        root.getStyleClass().add("root-pane");
        root.setPadding(new Insets(16));
        root.setHgap(16);
        root.setVgap(10);

        ColumnConstraints logColumn = new ColumnConstraints();
        logColumn.setPercentWidth(62);
        ColumnConstraints usersColumn = new ColumnConstraints();
        usersColumn.setPercentWidth(38);
        root.getColumnConstraints().addAll(logColumn, usersColumn);

        RowConstraints header = new RowConstraints();
        RowConstraints titles = new RowConstraints();
        RowConstraints content = new RowConstraints();
        content.setVgrow(Priority.ALWAYS);
        root.getRowConstraints().addAll(header, titles, content);

        // Row 0: title + status indicator
        Label title = new Label("Chat Server");
        title.getStyleClass().add("title");
        statusDot.getStyleClass().add("status-dot");
        statusLabel.getStyleClass().add("status-label");
        HBox status = new HBox(8, statusDot, statusLabel);
        status.setAlignment(Pos.CENTER_RIGHT);
        HBox headerBox = new HBox(title);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        root.add(headerBox, 0, 0);
        root.add(status, 1, 0);

        // Row 1: section titles
        Label logTitle = new Label("Activity log");
        logTitle.getStyleClass().add("section-title");
        usersTitle.getStyleClass().add("section-title");
        root.add(logTitle, 0, 1);
        root.add(usersTitle, 1, 1);

        // Row 2: log + users list
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.getStyleClass().add("log-area");
        root.add(logArea, 0, 2);

        ListView<String> userList = new ListView<>(users);
        userList.getStyleClass().add("user-list");
        userList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(String name, boolean empty) {
                super.updateItem(name, empty);
                if (empty || name == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(name);
                    setStyle("-fx-background-color: " + userColors.getOrDefault(name, "#ffffff") + ";");
                }
            }
        });
        root.add(userList, 1, 2);

        Scene scene = new Scene(root, 820, 520);
        scene.getStylesheets().add(getClass().getResource("/server.css").toExternalForm());
        return scene;
    }

    private void setStatus(boolean online, String text) {
        statusDot.setFill(online ? Color.web("#2e9d4f") : Color.web("#b0b0b0"));
        statusLabel.setText(text);
    }

    /** Random light colour, kept per user so it never changes while they are online. */
    private String randomColor() {
        Color color = Color.hsb(random.nextDouble() * 360, 0.28, 1.0);
        return String.format("#%02x%02x%02x",
                (int) Math.round(color.getRed() * 255),
                (int) Math.round(color.getGreen() * 255),
                (int) Math.round(color.getBlue() * 255));
    }

    // ---- ServerListener: called by the model on background threads ----

    @Override
    public void onLog(String message) {
        String line = "[" + LocalTime.now().format(LOG_TIME) + "] " + message + "\n";
        Platform.runLater(() -> logArea.appendText(line));
    }

    @Override
    public void onUserJoined(String username, boolean readOnly) {
        String display = readOnly ? username + " (read-only)" : username;
        userColors.put(display, randomColor());
        Platform.runLater(() -> {
            users.add(display);
            usersTitle.setText("Connected users (" + users.size() + ")");
        });
    }

    @Override
    public void onUserLeft(String username) {
        Platform.runLater(() -> {
            users.removeIf(u -> u.equals(username) || u.equals(username + " (read-only)"));
            usersTitle.setText("Connected users (" + users.size() + ")");
        });
        userColors.remove(username);
        userColors.remove(username + " (read-only)");
    }

    @Override
    public void stop() {
        if (server != null) {
            server.stop();
        }
        Platform.exit();
        System.exit(0);
    }
}
