# AI Prompt Log – TCP Chat (Java, TCP sockets, JavaFX)

| | |
|---|---|
| **Project** | Multi-client group chat: `TCPServer` and `TCPClient` (Maven, Java 21, JavaFX) |
| **AI assistant** | Claude (Anthropic), model Claude Opus 5.5, used through claude.ai |
| **Date** | 22 October 2026 |
| **Session length** | 20 prompts |

This log records every prompt given to the AI assistant during the development of
the project, in order, with the prompt text **verbatim**, a summary of what the AI
produced, the files affected, and how the result was checked.

**How the AI worked:** the assistant wrote the code in its own Linux workspace,
compiled it with JDK 21 and ran automated tests against real sockets (test programs
kept outside the repository). Two limits of that workspace apply to the whole log:

- **Maven Central was blocked** there, so `mvn package` was never run by the AI.
  The POMs use standard plugin versions and were checked as valid XML only.
- **JavaFX was not available** there, so the two JavaFX windows (`ServerApp`,
  `ClientApp`) were compile-checked without JavaFX but **never opened** by the AI.
  They must be run and checked by hand (see the test checklist, prompt 17).

---

## Summary

| # | Topic | Main output |
|---|-------|-------------|
| 1 | Layered architecture | Package/class design, layer communication (no files) |
| 2 | Maven projects | `TCPServer` and `TCPClient` POMs, packages, launchers |
| 3 | Text protocol | Protocol tables (no files) |
| 4 | `ServerConfig` | Runtime `config.properties` loading |
| 5 | `ServerListener` | Model → view event interface |
| 6 | `ChatServer` | `ServerSocket`, accept loop on a virtual thread |
| 7 | `ClientHandler` | One virtual thread per client, full protocol, `ClientRegistry` |
| 8 | Username handshake | `ConcurrentHashMap`, `Guest-n` read-only, safe duplicates |
| 9 | Message distribution | Stress tests; two slow-client bugs found and fixed |
| 10 | `allUsers`, `end`/`bye`, read-only | Verified existing behaviour (no code change) |
| 11 | Clean disconnections | `finally` cleanup, leave reasons, keep-alive |
| 12 | `ServerApp` (JavaFX) | GridPane UI, coloured user list, CSS, `TCPServer` launcher |
| 13 | `ClientConfig` | Command-line arguments or `config.properties`, `TCPClient` launcher |
| 14 | `ChatClient` | Client model, `MessageListener` |
| 15 | `ClientApp` (JavaFX) | Login + chat screens, Online indicator, CSS |
| 16 | Build and run | Instructions; config line added to the server log |
| 17 | Test checklist | Checklist document (claude.ai doc) |
| 18 | PlantUML diagrams | Interrupted by the user – no output |
| 19 | README | `README.md` |
| 20 | Prompt log | This file |

---

## Prompts

### 1 · Layered architecture

> I'm building a multi-client group chat in Java with TCP sockets and JavaFX. Server-client architecture: a central server receives a message from any client and distributes it to all connected clients. Design a layered architecture for both programs: a business-logic layer (model) for sockets, threads and protocol, a presentation layer (view) in JavaFX, and a configuration layer. The model must work independently of the view (Separation of Concerns). List the packages, the classes in each layer, and how the layers communicate.

**AI output:** A design answer in the chat. Proposed a Maven multi-module layout, the
`config` / `model` / `view` packages for both programs and their classes,
view → model via method calls, model → view via listener interfaces (Observer
pattern) with `Platform.runLater` in the view, and config injected by constructor.
Also proposed one reader thread per client plus a per-client outbound queue.

**Files:** none.

**Notes:** Several names in this first design changed later (e.g. a separate
`chat-common` module was not used; each project contains its own protocol handling).

---

### 2 · Maven projects

> Create two Maven projects, TCPServer and TCPClient, for IntelliJ IDEA. Use Java 21 (needed for virtual threads), add the javafx-controls dependency and the javafx-maven-plugin, and configure the maven-shade-plugin so `mvn package` produces an executable JAR. Create the packages `config`, `model` and `view` in each project.

**AI output:** Both projects with `pom.xml` (compiler plugin with release 21,
`javafx-controls` 21.0.6, `javafx-maven-plugin` 0.0.8, `maven-shade-plugin` 3.6.0),
the three packages, placeholder classes, and a `Launcher` class that does not
extend `Application` (needed for a fat JAR to start).

**Files:** `TCPServer/pom.xml`, `TCPClient/pom.xml`, `.gitignore`, placeholder
`config`, `model`, `view` classes, `Launcher.java` (later replaced in prompts 12 and 13).

**Verification:** Non-JavaFX classes compiled with `javac --release 21`; POMs
checked as well-formed XML. `mvn package` could not be run (Maven Central blocked).

**Notes:** The AI pointed out that the fat JAR only contains JavaFX natives for the
OS it is built on.

---

### 3 · Text protocol

> Define a simple line-based text protocol between client and server: the first line is the username (empty means read-only), `allUsers` asks for the user list, `end`/`bye` means leave, and anything else is a chat message. Server-to-client lines are chat messages formatted `[username HH:mm] text` plus system notices. Give it as a table.

**AI output:** Tables for client → server and server → client lines, the prefixes
`[` (chat), `* ` (notice) and `! ` (error), rules (username limits, duplicates,
read-only, server-side timestamp, 1,000-character limit) and an example session.

**Files:** none (the protocol was implemented in prompts 7–11).

---

### 4 · `ServerConfig`

> Write a `ServerConfig` class in the config package that loads the server IP address and port from `config.properties` at runtime with `java.util.Properties`, so the port can change without recompiling. Look for the file next to the JAR first, then fall back to a default in `src/main/resources`

**AI output:** `ServerConfig` record: built-in defaults, then the classpath file,
then a `config.properties` next to the running JAR (overriding key by key);
validation with error messages that name the source; `socketAddress()` helper.

**Files:** `config/ServerConfig.java` (replaced), `src/main/resources/config.properties`
(new), `view/ServerApp.java` (uses the config).

**Verification:** Built a real JAR and ran 6 cases: no file, port-only override,
host + port override, port `abc`, port `70000`, running from `target/classes`. All
behaved as intended.

---

### 5 · `ServerListener`

> Write a `ServerListener` interface in the model package with `onLog(String)`, `onUserJoined(String, boolean readOnly)` and `onUserLeft(String)`. The model will report events only through this interface, so it never depends on JavaFX.

**AI output:** The interface with a documented threading contract (called from
model threads, must be thread-safe and fast, exceptions are swallowed by the model)
and a no-op `ServerListener.NONE`.

**Files:** `model/ServerListener.java` (new).

**Verification:** Compiled with `-Xlint:all`; confirmed no `javafx` imports in the model package.

---

### 6 · `ChatServer`

> Write a `ChatServer` class that opens a `ServerSocket` on the configured port and accepts clients in a loop on a background virtual thread. Log 'Server Started' and 'Waiting for Client' through the listener.

**AI output:** `ChatServer` with `start()` (binds on the calling thread so
"port in use" is reported immediately, then starts the accept loop with
`Thread.ofVirtual()`), `stop()` (closes the socket to unblock `accept()`, closes all
clients), `setReuseAddress(true)`, `getLocalPort()`.

**Files:** `model/ChatServer.java` (new).

**Verification:** Test with real sockets: start, two clients connect, second server
on the same port fails with `Address already in use`, double start refused, stop
closes clients, restart on the same port works. The accept loop was confirmed to
run on a virtual thread.

---

### 7 · `ClientHandler` on virtual threads

> For each accepted socket, create a `ClientHandler` (Runnable) and start it on its own virtual thread with `Thread.ofVirtual()`. Explain why virtual threads suit this server

**AI output:** `ClientHandler` implementing the whole protocol from prompt 3, with a
reader thread and a writer thread per client and a bounded outbound queue;
`ClientRegistry` for the user list and broadcasting; `ChatServer.handleClient`
starting each handler with `Thread.ofVirtual()`. Explanation of why virtual threads
fit (I/O-bound clients, unmounting on blocking I/O, simple thread-per-client code,
no pool sizing) and their limits (CPU-bound work, pinning inside `synchronized` in
Java 21).

**Files:** `model/ClientHandler.java` (new), `model/ClientRegistry.java` (new),
`model/ChatServer.java` (changed), `model/ServerListener.java` (Javadoc).

**Verification:** Full protocol test (handshake, duplicates, invalid names, chat,
`allUsers`, read-only, `bye`, abrupt disconnect, truncation, stop) and a load test:
**5,000 clients (~10,000 virtual threads) ran on 13 platform threads**; one
broadcast reached all clients in about 0.1 s.

**Issues found:** The first load test failed because the *test* clients did not
read while thousands of join notices piled up, so the server correctly disconnected
them as too slow. The test was fixed (clients read continuously); no server change.

---

### 8 · Username handshake

> In `ClientHandler`, read the first line as the username. Store users in a thread-safe `ConcurrentHashMap`. An empty username joins in READ-ONLY MODE as Guest-n, and duplicate names must be handled safely. Log 'Welcome [User]' and notify the view.

**AI output:** All joined clients in one `ConcurrentHashMap<String, Member>` keyed by
lower-case name; atomic `putIfAbsent` for name claims; `Guest-n` names from an
`AtomicInteger`; the `Guest-` prefix reserved; log line `Welcome <name>` and
`onUserJoined` for the view.

**Files:** `model/ClientRegistry.java` (rewritten), `model/ClientHandler.java`
(rewritten), `model/ServerListener.java` (Javadoc).

**Verification:** **200 clients sending the same name at the same moment: exactly 1
accepted, 199 rejected.** 200 simultaneous guests: 200 distinct names. Earlier tests
re-run and passing.

**Notes – protocol changes:** guests are greeted `* Welcome Guest-n (read-only mode)`;
`allUsers` lists guests by name; names starting with `Guest-` are invalid.

---

### 9 · Message distribution

> Add message distribution: when a client sends a line, the server formats it with the sender's username and the current time as `[username HH:mm] message` and sends it to all active connections. Make writing to a client thread-safe.

**AI output:** Explained that distribution and thread-safe writing already existed
(one writer thread per socket, queue-based `send()`, ordering lock), then
stress-tested it.

**Files:** `model/ClientHandler.java` (changed).

**Verification:** Burst test with 20 senders × 200 messages and 21 receivers, then a
test with one client that never reads.

**Issues found and fixed (real bugs):**
1. The 1,000-line slow-client limit tripped during a legitimate burst, disconnecting
   clients that were reading → raised to **10,000 lines**.
2. Disconnecting a slow client could cut its last line in half (socket closed while
   the writer was mid-line) → the writer now finishes the line, sends
   `! Disconnected: you are not reading messages fast enough` and closes; 5-second
   backstop.

After the fix: 84,000 lines delivered, none malformed, identical order on every
client; only the stuck client is disconnected.

---

### 10 · `allUsers`, `end`/`bye`, read-only mode

> Handle `allUsers` (reply with the list of active users to that client only), `end`/`bye` (leave), and read-only mode (refuse messages from read-only users on the server side).

**AI output:** Pointed to the existing implementation in `ClientHandler.handleLine()`
and verified it with a focused test.

**Files:** none changed.

**Verification:** `allUsers` reaches only the requester; a guest's chat text is
refused with `! Read-only clients cannot send messages` and reaches nobody; `BYE`
and `end` produce `* Goodbye`, close the connection and notify the others.

---

### 11 · Clean disconnections

> Handle disconnections cleanly: on `bye`/`end`, a closed socket or an IOException, remove the user in a `finally` block, log it, notify the view and tell the other clients.

**AI output:** Reviewed the existing `finally` block and fixed three gaps:
- a `RuntimeException` would have left the socket and writer thread open → now
  caught, and `close()` always runs in `finally`;
- the log now gives the **reason** for every departure (`sent bye`,
  `closed the connection`, `connection error: …`, `server stopped`, `too slow…`);
- `setKeepAlive(true)` so the OS can detect vanished peers.

**Files:** `model/ClientHandler.java`, `model/ClientRegistry.java` (changed).

**Verification:** 8 exit paths tested (bye, END, socket closed, connection reset,
guest leaving, drop before name, duplicate rejected, server stop): correct log,
view event and notice to others in each case; connection count back to 0. All
earlier tests re-run and passing.

---

### 12 · `ServerApp` (JavaFX)

> Write `ServerApp` (JavaFX) implementing `ServerListener`. Use a GridPane with an activity log (TextArea) and a live ListView of connected usernames, where each user gets a random background colour. Style it with a CSS file. Use `Platform.runLater` for updates coming from the model's threads. Add a launcher class `TCPServer` so the app starts with `java TCPServer`

**AI output:** `ServerApp` (GridPane: header with status and Start/Stop, activity log,
user list with random pastel colours via a custom `ListCell`), batched log updates
(one `runLater` at a time), `server.css`, and `TCPServer` in the default package
(replacing `Launcher`); POM main class updated.

**Files:** `view/ServerApp.java` (rewritten), `resources/com/chat/server/view/server.css`
(new), `TCPServer.java` (new), `com/chat/server/Launcher.java` (deleted), `pom.xml`.

**Verification:** JavaFX unavailable to the AI: compiled without JavaFX and checked
that every error was a missing JavaFX symbol; no errors in the project's own code.
**The window was not opened by the AI – manual check required.**

---

### 13 · `ClientConfig`

> Write `ClientConfig`: the client starts with `java TCPClient <ServerIPAddress> <PortNumber>`. Use the arguments if there are two, otherwise read `config.properties`

**AI output:** `ClientConfig.fromArgs(...)`: two arguments win; otherwise the same
file lookup as the server; a wrong argument count falls back but is noted in the
source; a bad port on the command line is an error, not a silent fallback. Also the
client's `config.properties` and the `TCPClient` launcher in the default package.

**Files:** `config/ClientConfig.java` (rewritten), `resources/config.properties`
(new), `TCPClient.java` (new), `view/ClientApp.java` (uses it),
`com/chat/client/Launcher.java` (deleted), `pom.xml`.

**Verification:** 12 cases run from a real JAR (two arguments, host names, no
arguments, file next to the JAR, arguments vs file, 1 and 3 arguments, invalid ports
and host, broken file).

---

### 14 · `ChatClient`

> Write `ChatClient` in the model package with no JavaFX imports. `connect(username)` opens the socket and sends the username, then a virtual thread receives lines and passes them to a `MessageListener` interface (`onMessage`, `onDisconnected`). Add `send(text)`, where `end`/`bye` disconnect and read-only users can only use `allUsers`, and `disconnect()`, which notifies the server and closes the connection.

**AI output:** `MessageListener` (`onMessage(line)`, `onDisconnected(reason)`) and
`ChatClient` with receiver and writer virtual threads; `send()` returns a
`SendResult` (`SENT`, `LEFT`, `EMPTY`, `READ_ONLY`, `NOT_CONNECTED`); line breaks
in user text replaced by spaces; `disconnect()` sends `bye` and half-closes;
readable disconnect reasons; reconnect supported.

**Files:** `model/MessageListener.java` (new), `model/ChatClient.java` (new).

**Verification:** End-to-end test of real `ChatClient` instances against the real
`ChatServer`: join, chat, read-only refusal, `allUsers`, `Bye`, `disconnect()`,
reconnect, duplicate name, double connect, server stop, connection refused, unknown
host. Callbacks confirmed on a virtual thread.

**Issues found and fixed:** the unknown-host error only repeated the host name →
message now says `unknown host`.

---

### 15 · `ClientApp` (JavaFX)

> Write `ClientApp` (JavaFX, GridPane + CSS) implementing `MessageListener`. Show a login screen asking for the username before the full chat interface, with an empty name meaning read-only mode. Then show the chat screen: message list, a text area that sends with the SEND button or the Enter key, a LEAVE button, and an 'Online' status label with a coloured circle that turns Offline on disconnect. Disable sending in read-only mode.

**AI output:** Login screen (GridPane card) that switches to the chat only after the
server's welcome line and shows rejection reasons in place; chat screen (GridPane)
with styled message cells (own bubbles on the right, notices, errors), input that
sends on Enter or SEND, LEAVE / BACK TO LOGIN, Online/Offline label with a coloured
`Circle`; read-only disables input and SEND. Added a **Users** button so read-only
users can still use `allUsers`. `client.css`.

**Files:** `view/ClientApp.java` (rewritten), `resources/com/chat/client/view/client.css` (new).

**Verification:** Same compile check as prompt 12 (only missing-JavaFX errors).
**The window was not opened by the AI – manual check required.**

---

### 16 · Build and run instructions

> How do I build both executable JARs with Maven and run them from cmd as `java TCPServer` and `java TCPClient localhost 3000`? How do I show that changing config.properties needs no recompilation?

**AI output:** Step-by-step cmd instructions (`mvn clean package`, `set CLASSPATH=…`,
`java TCPServer`), a demonstration using `certutil -hashfile` before and after
editing `config.properties`, and pitfalls (`mvn clean` deletes `target/`, JavaFX
warning, OS-specific fat JAR).

**Files:** `view/ServerApp.java` – added a first log line
`Configuration: host:port (from <source>)` to make the demonstration visible.

---

### 17 · Test checklist

> Give me a test checklist covering every requirement: multiple clients, broadcast format, allUsers, read-only mode, end/bye/LEAVE, the Online indicator, the coloured user list and the server log.

**AI output:** A checklist document on claude.ai ("TCP Chat – Test Checklist") with
tick boxes in 11 sections: setup, multiple clients, broadcast format, allUsers,
read-only mode, leaving, Online indicator, server user list, server log,
configuration and launch, robustness.

**Files:** none in the repository (separate claude.ai document).

---

### 18 · PlantUML diagrams (interrupted)

> Write PlantUML for a class diagram of both projects and a deployment diagram showing the server node, the client nodes and the TCP/IP links. Optionally, a sequence diagram of join → message → leave.

**AI output:** none – the request was interrupted by the user before any work started.

---

### 19 · README

> Write a descriptive README.md with the features, architecture, protocol, configuration, build and run instructions

**AI output:** `README.md`: features, project structure, architecture (layers,
communication, threading model, thread safety), protocol tables and rules,
configuration (server, client, no-recompilation demo), requirements, build, run,
usage of both windows, troubleshooting, limitations.

**Files:** `README.md` (new).

---

### 20 · This prompt log

> can you create an ai promp log from this conv

**AI output:** This file.

**Files:** `AI_PROMPT_LOG.md` (new).

---

## Issues found during AI testing

| Prompt | Issue | Type | Resolution |
|--------|-------|------|------------|
| 7 | Load-test clients disconnected as "too slow" | Test design | Test clients read continuously; server unchanged |
| 9 | 1,000-line queue limit disconnected clients during bursts | **Bug** | Limit raised to 10,000 lines |
| 9 | Last line cut in half when a slow client was disconnected | **Bug** | Writer finishes the line, sends a notice, then closes |
| 9 | Slow-client test flooded every client, not only the stuck one | Test design | Sender paced at ~20,000 msg/s, larger messages |
| 11 | `RuntimeException` would leave socket and writer thread open | **Bug** | `close()` in `finally`; `RuntimeException` caught |
| 11 | All departures logged identically | Improvement | First known leave reason recorded and logged |
| 14 | Unknown host error repeated only the host name | Improvement | Message says `unknown host` |

## What still needs to be checked by hand

- `mvn clean package` for both projects (not possible in the AI's workspace).
- Both JavaFX windows: layout, CSS, colours, Online indicator, login flow, read-only
  mode – use the test checklist from prompt 17.
- Running across two machines on a network (all AI tests used `127.0.0.1`).
