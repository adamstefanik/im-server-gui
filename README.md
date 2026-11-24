![Java](https://img.shields.io/badge/Java-17+-blue.svg) ![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.2-6DB33F.svg) ![WebSocket](https://img.shields.io/badge/WebSocket-Native-orange.svg) ![License](https://img.shields.io/badge/license-MIT-blue.svg)

# IM Server GUI

Web-based interface for the Instant Message Server from Assignment 4 with real-time WebSocket communication and concurrent TCP Telnet client support.

## Features

**Web Interface**
- Dark-mode interface built with vanilla HTML5, CSS3, and JavaScript
- Nickname selection and change with server-side uniqueness check
- Discussion rooms with active channel switching (`#public` by default)
- Online user roster with client type indicators (Web vs Telnet)
- Direct private messaging view with dedicated chat history
- Quick command bar for one-click insertion of Assignment 4 commands
- No external frontend dependencies, frameworks, or build tools

**Server & Protocols**
- Dual listener architecture: HTTP & WebSocket on port 8080, raw TCP on port 33000
- Transparent message routing between browser clients and terminal Telnet sessions
- Group-based message isolation (messages only visible to clients sharing a room)
- Full protocol command parity: `#setMyName`, `#join`, `#leave`, `#groups`, `#sendPrivate`
- Thread-safe session management with automatic cleanup on disconnect

## Tech Stack

- **Java 17+**
- **Spring Boot 4** (Spring Web, Spring WebSocket)
- **Vanilla JavaScript** (native WebSocket API)
- **Pure CSS3** (no Tailwind, Bootstrap, or build pipelines)

## Requirements

- Java JDK 17+
- Modern web browser
- Optional: `nc` or `telnet` for terminal testing

## How to Run

**Gradle (Development)**
1. Navigate to the project directory:
   ```bash
   cd im-server-gui
   ```
2. Start the application:
   ```bash
   ./gradlew bootRun
   ```
3. Open `http://localhost:8080` in your browser.

**Standalone JAR**
1. Build the archive:
   ```bash
   ./gradlew build
   ```
2. Run the JAR:
   ```bash
   java -jar build/libs/im-server-gui-0.0.1-SNAPSHOT.jar
   ```
3. Open `http://localhost:8080` in your browser.

**Terminal Client (Optional)**
1. In a separate terminal window, connect to the TCP port:
   ```bash
   nc localhost 33000
   ```
   or:
   ```bash
   telnet localhost 33000
   ```
2. Enter your nickname when prompted (`Enter your name:`).
3. Send broadcast messages or commands (`#join dev`, `#sendPrivate WebUser hello`). Messages synchronize with the web interface in real time.

**Run Tests**
```bash
./gradlew test
```

## Structure

```
im-server-gui/
├── src/
│   ├── main/
│   │   ├── java/utb/fai/
│   │   │   ├── ActiveHandlers.java          # Thread-safe client registry and message router
│   │   │   ├── ClientSession.java           # Unified client session interface
│   │   │   ├── ImServerGuiApplication.java  # Spring Boot entry point and beans
│   │   │   ├── TcpClientSession.java        # TCP Telnet client handler (Assignment 4 compatible)
│   │   │   ├── TcpServerService.java        # Background TCP ServerSocket service on port 33000
│   │   │   ├── WebSocketChatHandler.java    # Native WebSocket handler for web clients
│   │   │   ├── WebSocketClientSession.java  # WebSocket client session wrapper
│   │   │   └── WebSocketConfig.java         # WebSocket endpoint registration (/ws)
│   │   └── resources/
│   │       ├── application.properties       # Server ports and configuration
│   │       └── static/
│   │           ├── css/
│   │           │   └── style.css            # Dark-mode styles and responsive layout
│   │           ├── js/
│   │           │   └── app.js               # WebSocket client logic and DOM updates
│   │           └── index.html               # Web interface structure and modals
│   └── test/
│       └── java/utb/fai/
│           └── ImServerGuiApplicationTests.java # TCP, WebSocket, and cross-platform tests
├── build.gradle                             # Dependencies and Gradle build config
├── gradlew                                  # Gradle wrapper script
└── README.md
```

## License

Made with Java by <a href="https://github.com/adamstefanik">Adam Samuel Štefánik</a>.
