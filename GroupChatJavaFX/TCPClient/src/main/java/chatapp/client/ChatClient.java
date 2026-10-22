package chatapp.client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

public class ChatClient extends Application {
    private static final String NL = String.valueOf((char) 0x2028);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private Stage stage;
    private TextField hostField, portField, usernameField;
    private Label loginMessage, userLabel, statusLabel, readOnlyBanner;
    private TextArea chatArea, inputArea;
    private Button connectButton, sendButton, leaveButton;
    private Circle statusDot;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private volatile boolean connected;
    private boolean readOnly;
    private String currentUsername = "";

    @Override public void start(Stage stage) {
        this.stage = stage;
        Properties config = loadConfig();
        String host = getParameters().getRaw().size() > 0 ? getParameters().getRaw().get(0) : config.getProperty("server.host", "localhost");
        String port = getParameters().getRaw().size() > 1 ? getParameters().getRaw().get(1) : config.getProperty("server.port", "5000");

        Label title = new Label("Group Chat");
        title.getStyleClass().add("title");
        hostField = new TextField(host);
        portField = new TextField(port);
        usernameField = new TextField();
        usernameField.setPromptText("Leave empty for read-only mode");
        connectButton = new Button("CONNECT");
        loginMessage = new Label("Enter a username to send messages, or leave it empty to join read-only.");
        loginMessage.setWrapText(true);
        connectButton.setOnAction(e -> connect());
        usernameField.setOnAction(e -> connect());
        GridPane form = new GridPane();
        form.setHgap(10); form.setVgap(12);
        form.add(new Label("Server IP / host"), 0, 0); form.add(hostField, 1, 0);
        form.add(new Label("Port"), 0, 1); form.add(portField, 1, 1);
        form.add(new Label("Username"), 0, 2); form.add(usernameField, 1, 2);
        form.add(connectButton, 1, 3);
        form.add(loginMessage, 0, 4, 2, 1);
        VBox login = new VBox(18, title, new Label("Connect to the central TCP server"), form);
        login.setPadding(new Insets(28));
        login.setAlignment(Pos.CENTER_LEFT);
        login.setMaxWidth(520);
        StackPane loginRoot = new StackPane(login);
        loginRoot.setPadding(new Insets(25));
        Scene scene = new Scene(loginRoot, 720, 460);
        applyCss(scene, "/client.css");
        stage.setTitle("Group Chat");
        stage.setScene(scene);
        stage.setMinWidth(520);
        stage.setMinHeight(420);
        stage.setOnCloseRequest(e -> disconnect(true, false));
        stage.show();
    }

    private Properties loadConfig() {
        Properties p = new Properties();
        try (InputStream input = getClass().getResourceAsStream("/config.properties")) {
            if (input != null) p.load(input);
        } catch (IOException ignored) {}
        return p;
    }

    private void applyCss(Scene scene, String resource) {
        var css = getClass().getResource(resource);
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
    }

    private void connect() {
        if (connected) return;
        String host = hostField.getText().trim();
        String name = usernameField.getText().trim();
        int port;
        try { port = Integer.parseInt(portField.getText().trim()); }
        catch (NumberFormatException e) { loginMessage.setText("Port must be a number."); return; }
        if (host.isBlank()) { loginMessage.setText("Enter a server IP or host."); return; }
        if (port < 1 || port > 65535) { loginMessage.setText("Port must be between 1 and 65535."); return; }

        connectButton.setDisable(true);
        loginMessage.setText("Connecting...");
        Thread.startVirtualThread(() -> {
            try {
                Socket newSocket = new Socket();
                newSocket.connect(new InetSocketAddress(host, port), 5000);
                BufferedReader reader = new BufferedReader(new InputStreamReader(newSocket.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(newSocket.getOutputStream(), StandardCharsets.UTF_8), true);
                writer.println(name.isEmpty() ? "READONLY" : "JOIN " + name);
                String reply = reader.readLine();
                if (reply == null) throw new IOException("Server closed the connection.");
                if (reply.startsWith("ERROR ")) {
                    newSocket.close();
                    Platform.runLater(() -> {
                        loginMessage.setText(reply.substring(6));
                        connectButton.setDisable(false);
                    });
                    return;
                }
                socket = newSocket; in = reader; out = writer;
                connected = true; readOnly = name.isEmpty(); currentUsername = name;
                Platform.runLater(() -> showChat(reply));
                listen();
            } catch (IOException ex) {
                Platform.runLater(() -> {
                    loginMessage.setText("Could not connect: " + ex.getMessage());
                    connectButton.setDisable(false);
                });
            }
        });
    }

    private void showChat(String firstReply) {
        userLabel = new Label(readOnly ? "Guest — READ-ONLY MODE" : "User: " + currentUsername);
        userLabel.getStyleClass().add("user-label");
        statusDot = new Circle(6, Color.LIMEGREEN);
        statusLabel = new Label("Online");
        HBox status = new HBox(7, statusDot, statusLabel);
        status.setAlignment(Pos.CENTER_RIGHT);
        BorderPane header = new BorderPane();
        header.setLeft(userLabel); header.setRight(status);

        chatArea = new TextArea();
        chatArea.setEditable(false);
        chatArea.setWrapText(true);
        chatArea.getStyleClass().add("chat-area");

        readOnlyBanner = new Label("READ-ONLY MODE — You can read messages but cannot send.");
        readOnlyBanner.getStyleClass().add("readonly-banner");
        readOnlyBanner.setVisible(readOnly);
        readOnlyBanner.setManaged(readOnly);

        inputArea = new TextArea();
        inputArea.setPromptText("Type a message… (Enter to send, Shift+Enter for a new line)");
        inputArea.setPrefRowCount(3);
        inputArea.setWrapText(true);
        inputArea.setDisable(readOnly);
        inputArea.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER && !e.isShiftDown()) {
                e.consume();
                sendCurrentInput();
            }
        });

        sendButton = new Button("SEND");
        sendButton.setDisable(readOnly);
        sendButton.setOnAction(e -> sendCurrentInput());
        leaveButton = new Button("LEAVE");
        leaveButton.setOnAction(e -> disconnect(true, true));
        VBox buttons = new VBox(8, sendButton, leaveButton);
        buttons.setAlignment(Pos.CENTER);
        HBox compose = new HBox(10, inputArea, buttons);
        HBox.setHgrow(inputArea, Priority.ALWAYS);

        VBox bottom = new VBox(8, readOnlyBanner, compose);
        bottom.setPadding(new Insets(10, 0, 0, 0));
        BorderPane root = new BorderPane();
        root.setTop(header); root.setCenter(chatArea); root.setBottom(bottom);
        root.setPadding(new Insets(16));
        BorderPane.setMargin(header, new Insets(0, 0, 12, 0));
        Scene scene = new Scene(root, 720, 540);
        applyCss(scene, "/client.css");
        stage.setTitle("Group Chat — " + (readOnly ? "READ-ONLY" : currentUsername));
        stage.setScene(scene);
        append("*** Connected to server.");
        handleLine(firstReply);
        inputArea.requestFocus();
    }

    private void listen() {
        try {
            String line;
            while (connected && (line = in.readLine()) != null) {
                String received = line;
                Platform.runLater(() -> handleLine(received));
            }
        } catch (IOException ignored) {}
        if (connected) Platform.runLater(() -> disconnect(false, true));
    }

    private void handleLine(String line) {
        if (line.startsWith("MSG ")) {
            String[] parts = line.substring(4).split("\\t", 3);
            if (parts.length == 3) {
                append("[" + parts[1] + "] " + parts[0] + ": " + parts[2].replace(NL, "\n"));
            } else {
                append(line.substring(4).replace(NL, "\n"));
            }
        } else if (line.startsWith("INFO ")) {
            append("*** " + line.substring(5));
        } else if (line.startsWith("USERS ")) {
            append("Active users: " + line.substring(6));
        } else if (line.startsWith("ERROR ")) {
            append("!! " + line.substring(6));
        }
    }

    private void sendCurrentInput() {
        if (!connected || readOnly || out == null) return;
        String message = inputArea.getText().trim();
        inputArea.clear();
        if (message.isEmpty()) return;
        if (message.equalsIgnoreCase("end") || message.equalsIgnoreCase("bye")) {
            disconnect(true, true);
        } else if (message.equalsIgnoreCase("allUsers")) {
            out.println("ALLUSERS");
        } else {
            append("[" + LocalTime.now().format(TIME) + "] Me: " + message);
            out.println("MSG " + message.replace("\r", "").replace("\n", NL));
        }
    }

    private void append(String text) {
        if (chatArea == null) return;
        chatArea.appendText(text + "\n");
        chatArea.positionCaret(chatArea.getLength());
    }

    private void disconnect(boolean notifyServer, boolean showLogin) {
        boolean wasConnected = connected;
        connected = false;
        try {
            if (notifyServer && out != null && wasConnected) out.println("BYE");
            if (socket != null) socket.close();
        } catch (IOException ignored) {}
        if (showLogin && stage != null) {
            stage.setTitle("Group Chat");
            Scene scene = stage.getScene();
            // Build the login view again by restarting the application window's scene.
            Label title = new Label("Group Chat");
            title.getStyleClass().add("title");
            hostField = hostField == null ? new TextField("localhost") : hostField;
            portField = portField == null ? new TextField("5000") : portField;
            usernameField = usernameField == null ? new TextField() : usernameField;
            connectButton = new Button("CONNECT");
            loginMessage = new Label("Disconnected. Connect again when ready.");
            GridPane form = new GridPane();
            form.setHgap(10); form.setVgap(12);
            form.add(new Label("Server IP / host"), 0, 0); form.add(hostField, 1, 0);
            form.add(new Label("Port"), 0, 1); form.add(portField, 1, 1);
            form.add(new Label("Username"), 0, 2); form.add(usernameField, 1, 2);
            form.add(connectButton, 1, 3); form.add(loginMessage, 0, 4, 2, 1);
            connectButton.setOnAction(e -> connect());
            usernameField.setOnAction(e -> connect());
            VBox login = new VBox(18, title, new Label("Connect to the central TCP server"), form);
            login.setPadding(new Insets(28)); login.setMaxWidth(520);
            StackPane loginRoot = new StackPane(login); loginRoot.setPadding(new Insets(25));
            Scene loginScene = new Scene(loginRoot, 720, 460);
            applyCss(loginScene, "/client.css");
            stage.setScene(loginScene);
        }
    }

    @Override public void stop() { disconnect(true, false); }

    public static void main(String[] args) { launch(args); }
}
