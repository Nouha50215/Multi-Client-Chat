package ma.aui.chat.client.view;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import ma.aui.chat.client.config.ClientConfig;
import ma.aui.chat.client.model.ChatClient;
import ma.aui.chat.client.model.MessageListener;

import java.io.IOException;

/**
 * JavaFX view of the client: a login screen, then the chat screen.
 *
 * The view only calls ChatClient (connect / send / disconnect) and receives
 * events through MessageListener. Those callbacks come from the model's
 * background thread, so UI updates go through Platform.runLater.
 */
public class ClientApp extends Application implements MessageListener {

    private Stage stage;
    private ChatClient client;

    // chat screen controls
    private final ObservableList<String> messages = FXCollections.observableArrayList();
    private ListView<String> messageList;
    private final Circle statusDot = new Circle(7);
    private final Label statusLabel = new Label("Online");
    private TextArea input;
    private Button sendButton;
    private Button leaveButton;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        ClientConfig config = ClientConfig.resolve(getParameters().getRaw());
        client = new ChatClient(config.host(), config.port(), this);

        stage.setTitle("Chat Client");
        stage.setScene(buildLoginScene());
        stage.setOnCloseRequest(e -> client.disconnect());
        stage.show();
    }

    // ------------------------------------------------------------------ login screen

    private Scene buildLoginScene() {
        GridPane grid = new GridPane();
        grid.getStyleClass().addAll("root-pane", "login-pane");
        grid.setAlignment(Pos.CENTER);
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new Insets(32));

        Label title = new Label("Group Chat");
        title.getStyleClass().add("title");
        Label server = new Label("Server " + client.getHost() + ":" + client.getPort());
        server.getStyleClass().add("muted");

        TextField usernameField = new TextField();
        usernameField.setPromptText("Enter your username");
        usernameField.setPrefWidth(260);

        Button joinButton = new Button("Join chat");
        joinButton.getStyleClass().add("primary-button");
        joinButton.setDefaultButton(true); // Enter in the field = Join

        Label hint = new Label("Leave the username empty to join in READ-ONLY MODE.");
        hint.getStyleClass().add("muted");
        Label error = new Label();
        error.getStyleClass().add("error");

        joinButton.setOnAction(e -> {
            try {
                client.connect(usernameField.getText());
                stage.setScene(buildChatScene());
                stage.setTitle(client.isReadOnly() ? "Chat Client - READ-ONLY MODE" : "Chat Client - " + client.getUsername());
            } catch (IOException ex) {
                error.setText("Cannot reach the server at " + client.getHost() + ":" + client.getPort());
            }
        });

        grid.add(title, 0, 0, 2, 1);
        grid.add(server, 0, 1, 2, 1);
        grid.add(usernameField, 0, 2);
        grid.add(joinButton, 1, 2);
        grid.add(hint, 0, 3, 2, 1);
        grid.add(error, 0, 4, 2, 1);
        GridPane.setHalignment(title, HPos.LEFT);

        Scene scene = new Scene(grid, 520, 300);
        scene.getStylesheets().add(getClass().getResource("/client.css").toExternalForm());
        return scene;
    }

    // ------------------------------------------------------------------ chat screen

    private Scene buildChatScene() {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("root-pane");
        grid.setPadding(new Insets(14));
        grid.setHgap(10);
        grid.setVgap(10);

        ColumnConstraints inputColumn = new ColumnConstraints();
        inputColumn.setHgrow(Priority.ALWAYS);
        ColumnConstraints buttonColumn = new ColumnConstraints();
        buttonColumn.setMinWidth(96);
        grid.getColumnConstraints().addAll(inputColumn, buttonColumn);

        RowConstraints headerRow = new RowConstraints();
        RowConstraints messagesRow = new RowConstraints();
        messagesRow.setVgrow(Priority.ALWAYS);
        RowConstraints inputRow = new RowConstraints();
        grid.getRowConstraints().addAll(headerRow, messagesRow, inputRow);

        // Row 0: who am I + Online indicator
        Label who = new Label(client.isReadOnly() ? "READ-ONLY MODE" : "Logged in as " + client.getUsername());
        who.getStyleClass().add(client.isReadOnly() ? "read-only-badge" : "user-label");
        statusDot.getStyleClass().addAll("status-dot", "online");
        statusLabel.getStyleClass().add("status-label");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(10, who, spacer, statusDot, statusLabel);
        header.setAlignment(Pos.CENTER_LEFT);
        grid.add(header, 0, 0, 2, 1);

        // Row 1: messages
        messageList = new ListView<>(messages);
        messageList.getStyleClass().add("message-list");
        messageList.setCellFactory(list -> {
            MessageCell cell = new MessageCell();
            cell.prefWidthProperty().bind(list.widthProperty().subtract(24)); // lets long lines wrap
            return cell;
        });
        grid.add(messageList, 0, 1, 2, 1);

        // Row 2: input + buttons
        input = new TextArea();
        input.setPrefRowCount(2);
        input.setWrapText(true);
        input.setPromptText(client.isReadOnly()
                ? "READ-ONLY MODE: you can only type allUsers, end or bye"
                : "Type a message, allUsers, or end / bye to leave (Enter to send)");
        input.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER && !e.isShiftDown()) {
                e.consume();      // Enter sends, Shift+Enter makes a new line
                sendCurrent();
            }
        });

        sendButton = new Button("SEND");
        sendButton.getStyleClass().add("primary-button");
        sendButton.setMaxWidth(Double.MAX_VALUE);
        sendButton.setOnAction(e -> sendCurrent());

        leaveButton = new Button("LEAVE");
        leaveButton.getStyleClass().add("leave-button");
        leaveButton.setMaxWidth(Double.MAX_VALUE);
        leaveButton.setOnAction(e -> {
            if (client.isConnected()) {
                client.disconnect();
            } else {
                stage.close();
            }
        });

        if (client.isReadOnly()) {
            sendButton.setDisable(true); // read-only users cannot send chat messages
        }

        VBox buttons = new VBox(8, sendButton, leaveButton);
        grid.add(input, 0, 2);
        grid.add(buttons, 1, 2);

        Scene scene = new Scene(grid, 640, 520);
        scene.getStylesheets().add(getClass().getResource("/client.css").toExternalForm());
        Platform.runLater(input::requestFocus);
        return scene;
    }

    private void sendCurrent() {
        String text = input.getText();
        input.clear();
        client.send(text);
    }

    /** Styles own messages, other people's messages and system notices differently. */
    private class MessageCell extends ListCell<String> {
        @Override
        protected void updateItem(String line, boolean empty) {
            super.updateItem(line, empty);
            getStyleClass().removeAll("system-message", "own-message", "other-message");
            if (empty || line == null) {
                setText(null);
                return;
            }
            setText(line);
            setWrapText(true);
            if (line.startsWith("SYSTEM:") || line.startsWith("***")) {
                getStyleClass().add("system-message");
            } else if (!client.isReadOnly() && line.startsWith("[" + client.getUsername() + " ")) {
                getStyleClass().add("own-message");
            } else {
                getStyleClass().add("other-message");
            }
        }
    }

    // ------------------------------------------------------------------ MessageListener

    @Override
    public void onMessage(String line) {
        Platform.runLater(() -> {
            messages.add(line);
            if (messageList != null) {
                messageList.scrollTo(messages.size() - 1);
            }
        });
    }

    @Override
    public void onDisconnected() {
        Platform.runLater(() -> {
            messages.add("SYSTEM: Disconnected from the server.");
            statusDot.getStyleClass().remove("online");
            statusDot.getStyleClass().add("offline");
            statusLabel.setText("Offline");
            if (input != null) {
                input.setDisable(true);
                sendButton.setDisable(true);
                leaveButton.setText("EXIT");
            }
        });
    }

    @Override
    public void stop() {
        client.disconnect();
        Platform.exit();
        System.exit(0);
    }
}
