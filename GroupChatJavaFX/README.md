# JavaFX TCP Group Chat

This workspace contains two Maven projects:
- `TCPServer`: JavaFX server dashboard + TCP server model.
- `TCPClient`: JavaFX chat client.

## Requirements
- JDK 21 or newer (virtual threads are used by the server).
- Maven 3.9+.
- Internet access on the first Maven build so dependencies can be downloaded.

## Open in IntelliJ IDEA
1. Extract the ZIP.
2. Open the **root** `GroupChatJavaFX` folder (the one containing the parent `pom.xml`).
3. Set Project SDK to JDK 21 or newer.
4. Allow IntelliJ to import Maven projects.

## Run
Open a terminal in the extracted root folder.

Start the server dashboard:
```bash
mvn -pl TCPServer javafx:run
```
Click **START SERVER** in the dashboard.

Start a client in another terminal:
```bash
mvn -pl TCPClient javafx:run
```
Run the client command again in another terminal to create additional client windows.

You can pass client host and port as JavaFX application arguments if running with a configured run profile. The client login also displays the values from `TCPClient/src/main/resources/config.properties`.

## Network setup
Default port is 5000. Update:
- `TCPServer/src/main/resources/config.properties` (`server.port`)
- `TCPClient/src/main/resources/config.properties` (`server.host`, `server.port`)

For clients on other computers, use the server computer's LAN IP instead of `localhost`, and allow the port through the server computer's firewall.

## Features included
- TCP `ServerSocket` / `Socket` networking.
- JavaFX client and server dashboard, styled with CSS.
- Concurrent client handling using Java virtual threads.
- Username uniqueness check.
- Blank username enters read-only mode; the server rejects attempted messages from guests.
- `allUsers`, `end`, `bye`, SEND button, Enter-to-send, LEAVE button.
- Server-side timestamps and sender labels for distributed messages.
- Server connected-user ListView, random per-user background colors, and activity log.
- Configurable port/host defaults.

## Build
From the root:
```bash
mvn clean package
```
This produces module build outputs under each module's `target` folder. The JavaFX Maven plugin is intended for running the applications; additional packaging configuration may be needed if your instructor requires self-contained executable JARs with all JavaFX runtime dependencies.

## Protocol
Client to server: `JOIN <username>`, `READONLY`, `MSG <text>`, `ALLUSERS`, `BYE`.
Server to client: `INFO <text>`, `MSG <username>\t<HH:mm:ss>\t<text>`, `USERS <list>`, `ERROR <text>`.
