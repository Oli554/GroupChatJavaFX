package chatapp.server;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.scene.layout.*;

import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import java.util.Random;

public class ServerUI extends Application {
    private final ObservableList<String> usernames = FXCollections.observableArrayList();
    private final ObservableList<String> logLines = FXCollections.observableArrayList();
    private ChatServer server;
    private Label stateLabel;
    private ListView<String> usersView;
    private ListView<String> logView;
    private Button startButton;
    private Button stopButton;
    private int port = 5000;

    @Override public void start(Stage stage) {
        loadConfig();
        Label title = new Label("Group Chat — Server Dashboard");
        title.getStyleClass().add("title");

        Circle statusDot = new Circle(6, Color.web("#dc3545"));
        stateLabel = new Label("Stopped");
        HBox state = new HBox(8, statusDot, stateLabel);
        state.getStyleClass().add("status");

        usersView = new ListView<>(usernames);
        usersView.setPlaceholder(new Label("No connected users"));
        usersView.setCellFactory(list -> new ListCell<>() {
            private final Random random = new Random();
            private Color background;
            @Override protected void updateItem(String name, boolean empty) {
                super.updateItem(name, empty);
                if (empty || name == null) {
                    setText(null); setStyle("");
                } else {
                    setText("●  " + name);
                    // Each ListCell receives a random readable background colour.
                    if (background == null) {
                        background = Color.hsb(random.nextDouble() * 360, 0.24, 0.98);
                    }
                    setStyle("-fx-background-color: " + toRgb(background) + "; -fx-padding: 8;");
                }
            }
        });

        logView = new ListView<>(logLines);
        logView.setPlaceholder(new Label("Server activity will appear here"));
        startButton = new Button("START SERVER");
        stopButton = new Button("STOP SERVER");
        stopButton.setDisable(true);
        startButton.setOnAction(e -> startServer(statusDot));
        stopButton.setOnAction(e -> stopServer(statusDot));

        HBox controls = new HBox(10, startButton, stopButton, state);
        controls.setPadding(new Insets(10, 0, 10, 0));

        VBox usersPanel = new VBox(8, new Label("CONNECTED USERS"), usersView);
        VBox.setVgrow(usersView, Priority.ALWAYS);
        VBox logsPanel = new VBox(8, new Label("SERVER ACTIVITY LOG"), logView);
        VBox.setVgrow(logView, Priority.ALWAYS);
        usersPanel.getStyleClass().add("panel");
        logsPanel.getStyleClass().add("panel");
        HBox content = new HBox(14, usersPanel, logsPanel);
        HBox.setHgrow(usersPanel, Priority.ALWAYS);
        HBox.setHgrow(logsPanel, Priority.ALWAYS);
        usersPanel.setPrefWidth(270);
        GridPane root = new GridPane();

        root.setPadding(new Insets(20));
        root.setHgap(14);
        root.setVgap(12);

// Define the columns
        ColumnConstraints leftColumn = new ColumnConstraints();
        leftColumn.setPercentWidth(35);

        ColumnConstraints rightColumn = new ColumnConstraints();
        rightColumn.setPercentWidth(65);

        root.getColumnConstraints().addAll(leftColumn, rightColumn);

// Header: spans both columns
        root.add(title, 0, 0, 2, 1);

        Label subtitle = new Label(
                "TCP server monitoring and connection management"
        );
        root.add(subtitle, 0, 1, 2, 1);

// Controls: spans both columns
        root.add(controls, 0, 2, 2, 1);

// User panel: left column
        root.add(usersPanel, 0, 3);

// Server logs: right column
        root.add(logsPanel, 1, 3);

// Allow the panels to expand vertically
        GridPane.setVgrow(usersPanel, Priority.ALWAYS);
        GridPane.setVgrow(logsPanel, Priority.ALWAYS);

        GridPane.setHgrow(usersPanel, Priority.ALWAYS);
        GridPane.setHgrow(logsPanel, Priority.ALWAYS);

        root.setMaxWidth(Double.MAX_VALUE);
        root.setMaxHeight(Double.MAX_VALUE);
        Scene scene = new Scene(root, 900, 600);
        var css = getClass().getResource("/server.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        stage.setTitle("Group Chat Server");
        stage.setScene(scene);
        stage.setMinWidth(720);
        stage.setMinHeight(480);
        stage.setOnCloseRequest(e -> stopServer(statusDot));
        stage.show();
        log("Server dashboard ready. Press START SERVER.");
    }

    private void loadConfig() {
        Properties properties = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/config.properties")) {
            if (in != null) properties.load(in);
            port = Integer.parseInt(properties.getProperty("server.port", "5000").trim());
        } catch (Exception e) {
            port = 5000;
        }
    }

    private void startServer(Circle dot) {
        if (server != null && server.isRunning()) return;
        server = new ChatServer(port, this::log, this::updateUsers);
        server.start();
        dot.setFill(Color.LIMEGREEN);
        stateLabel.setText("Running on port " + port);
        startButton.setDisable(true);
        stopButton.setDisable(false);
    }

    private void stopServer(Circle dot) {
        if (server != null) server.stop();
        dot.setFill(Color.web("#dc3545"));
        stateLabel.setText("Stopped");
        startButton.setDisable(false);
        stopButton.setDisable(true);
    }

    private void updateUsers(List<String> names) {
        Platform.runLater(() -> usernames.setAll(names));
    }

    private void log(String message) {
        Platform.runLater(() -> {
            logLines.add(message);
            if (logView != null && !logLines.isEmpty()) logView.scrollTo(logLines.size() - 1);
        });
    }

    private String toRgb(Color color) {
        return String.format("#%02X%02X%02X",
                (int) Math.round(color.getRed() * 255),
                (int) Math.round(color.getGreen() * 255),
                (int) Math.round(color.getBlue() * 255));
    }

    public static void main(String[] args) { launch(args); }
}
