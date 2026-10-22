package chatapp.server;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * TCP server model. No JavaFX types are used here: the networking model can
 * operate independently from the ServerUI presentation layer.
 */
public final class ChatServer {
    private static final String NL = String.valueOf((char) 0x2028);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final int port;
    private final Consumer<String> logger;
    private final Consumer<List<String>> usersListener;
    private final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();
    private volatile boolean running;
    private ServerSocket serverSocket;

    public ChatServer(int port, Consumer<String> logger, Consumer<List<String>> usersListener) {
        this.port = port;
        this.logger = logger == null ? s -> {} : logger;
        this.usersListener = usersListener == null ? users -> {} : usersListener;
    }

    public void start() {
        if (running) return;
        running = true;
        Thread.startVirtualThread(() -> {
            try (ServerSocket listener = new ServerSocket()) {
                serverSocket = listener;
                listener.setReuseAddress(true);
                listener.bind(new InetSocketAddress(port));
                log("Server Started on port " + port);
                while (running) {
                    Socket socket = listener.accept();
                    log("Waiting for Client — connection from " + socket.getRemoteSocketAddress());
                    Thread.startVirtualThread(new ClientHandler(socket));
                }
            } catch (SocketException e) {
                if (running) log("Server socket error: " + e.getMessage());
            } catch (IOException e) {
                if (running) log("Server error: " + e.getMessage());
            } finally {
                running = false;
                log("Server stopped.");
            }
        });
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {}
        for (ClientHandler client : clients) client.closeSocket();
    }

    public boolean isRunning() { return running; }

    private void log(String message) {
        logger.accept("[" + LocalTime.now().format(TIME) + "] " + message);
    }

    private void notifyUsers() {
        List<String> names = clients.stream()
                .filter(c -> c.registered && c.username != null)
                .map(c -> c.username)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        usersListener.accept(names);
    }

    private boolean usernameTaken(String name) {
        return clients.stream().anyMatch(c -> c.username != null && c.username.equalsIgnoreCase(name));
    }

    private String activeUsers() {
        List<String> names = clients.stream()
                .filter(c -> c.registered && c.username != null)
                .map(c -> c.username)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        long guests = clients.stream().filter(c -> c.registered && c.username == null).count();
        String result = names.isEmpty() ? "(none)" : String.join(", ", names);
        if (guests > 0) result += "  [+" + guests + " read-only viewer" + (guests == 1 ? "" : "s") + "]";
        return result;
    }

    private void broadcast(String line, ClientHandler except) {
        for (ClientHandler client : clients) {
            if (client != except && client.registered) client.send(line);
        }
    }

    private final class ClientHandler implements Runnable {
        private final Socket socket;
        private PrintWriter out;
        private volatile String username; // null means read-only
        private volatile boolean registered;

        private ClientHandler(Socket socket) { this.socket = socket; }

        private synchronized void send(String line) {
            if (out != null) {
                out.println(line);
                if (out.checkError()) log("Could not send data to " + socket.getRemoteSocketAddress());
            }
        }

        @Override public void run() {
            try (Socket ignored = socket;
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                 PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {
                out = writer;
                String first = in.readLine();
                if (first == null) return;

                if (first.startsWith("JOIN ")) {
                    String requested = first.substring(5).trim();
                    if (requested.isEmpty()) { send("ERROR Username cannot be empty"); return; }
                    synchronized (ChatServer.this) {
                        if (usernameTaken(requested)) {
                            send("ERROR Username '" + requested + "' is already taken");
                            return;
                        }
                        username = requested;
                        clients.add(this);
                        registered = true;
                    }
                    send("INFO Welcome, " + username + "! Type allUsers to see who is online.");
                    broadcast("INFO " + username + " joined the chat", this);
                    log("Welcome " + username);
                    notifyUsers();
                } else if (first.equals("READONLY")) {
                    clients.add(this);
                    registered = true;
                    send("INFO You are in READ-ONLY MODE. You can read messages but not send.");
                    log("Read-only viewer connected");
                    notifyUsers();
                } else {
                    send("ERROR Bad handshake");
                    return;
                }

                String line;
                while (running && (line = in.readLine()) != null) {
                    if (line.startsWith("MSG ")) {
                        if (username == null) {
                            send("ERROR You are in READ-ONLY MODE and cannot send messages");
                            continue;
                        }
                        String message = line.substring(4).replace(NL, "\n");
                        // Server adds the sender identity and timestamp as required.
                        String formatted = "MSG " + username + "\t" + LocalTime.now().format(TIME) + "\t" + message.replace("\n", NL);
                        broadcast(formatted, this);
                    } else if (line.equals("ALLUSERS")) {
                        send("USERS " + activeUsers());
                    } else if (line.equals("BYE")) {
                        break;
                    }
                }
            } catch (IOException e) {
                log("Connection ended: " + socket.getRemoteSocketAddress());
            } finally {
                boolean wasRegistered = registered;
                registered = false;
                clients.remove(this);
                closeSocket();
                if (wasRegistered) {
                    if (username != null) {
                        broadcast("INFO " + username + " left the chat", null);
                        log(username + " disconnected");
                    } else {
                        log("Read-only viewer disconnected");
                    }
                    notifyUsers();
                }
            }
        }

        private void closeSocket() {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }
}
