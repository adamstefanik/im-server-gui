[![Java](https://img.shields.io/badge/Java-17%2B-blue?style=flat)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.2-6DB33F?style=flat&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![WebSocket](https://img.shields.io/badge/WebSocket-Native-orange?style=flat)](https://developer.mozilla.org/en-US/docs/Web/API/WebSockets_API)
[![License](https://img.shields.io/badge/License-MIT-green?style=flat)](LICENSE)

# Instant Message Server GUI

A modern, web-enabled Instant Message server featuring an interactive dark-mode Web GUI and transparent cross-platform communication with legacy TCP Telnet clients.

## Features

**Core Messaging**
- Instant room-based messaging with automatic enrollment into the default public channel.
- Multi-room routing ensuring messages are isolated to participants sharing discussion groups.
- One-to-one private direct messaging between any connected users.

**Cross-Platform Interoperability**
- Concurrent support for both modern Web browser clients and TCP Telnet clients.
- Full protocol parity supporting CLI commands (`#setMyName`, `#join`, `#leave`, `#groups`, `#sendPrivate`).
- Unified routing engine synchronizing messages transparently across Web and Telnet connections.

**Interactive Web Interface**
- Responsive dark-mode interface with zero external CSS/JS dependencies.
- Dynamic channel switching, user presence roster, and dedicated Direct Message views.
- Interactive modal dialogs and quick-command helpers for streamlined user interaction.

## Tech Stack

- **Java 17+**: Core backend execution environment.
- **Spring Boot 4**: Application framework providing embedded Tomcat and dependency management.
- **Spring WebSocket**: Native low-latency WebSocket communication layer.
- **HTML5 & CSS3**: Vanilla, dependency-free responsive frontend design.

## Requirements

- Java JDK 17 or higher
- Modern web browser (Chrome, Firefox, Safari, Edge)
- Optional: Telnet client or Netcat (`nc`) for terminal testing

## How to Run

1. Navigate to the project directory:
   ```bash
   cd im-server-gui
   ```

2. Build and start the server using the Gradle wrapper:
   ```bash
   ./gradlew bootRun
   ```

   Alternatively, package and run the standalone executable archive:
   ```bash
   ./gradlew build
   java -jar build/libs/im-server-gui-0.0.1-SNAPSHOT.jar
   ```

3. Open the Web GUI in your browser:
   ```text
   http://localhost:8080
   ```
   Choose a unique nickname without spaces and begin chatting in `#public`, creating rooms, or messaging online users.

4. Optional: Connect a TCP Telnet client in a separate terminal:
   ```bash
   nc localhost 33000
   # or
   telnet localhost 33000
   ```
   Enter your name when prompted and exchange messages with both web and terminal clients in real time.

## Structure

```text
im-server-gui/
├── src/
│   ├── main/
│   │   ├── java/utb/fai/
│   │   │   ├── ActiveHandlers.java          # Thread-safe client registry and message routing engine
│   │   │   ├── ClientSession.java           # Unified abstraction for Web and TCP clients
│   │   │   ├── ImServerGuiApplication.java  # Spring Boot entry point and bean configuration
│   │   │   ├── TcpClientSession.java        # TCP Telnet client session handler (Task 4 compatible)
│   │   │   ├── TcpServerService.java        # Background TCP ServerSocket service on port 33000
│   │   │   ├── WebSocketChatHandler.java    # Text WebSocket handler for browser clients
│   │   │   ├── WebSocketClientSession.java  # WebSocket client session representation
│   │   │   └── WebSocketConfig.java         # WebSocket endpoint configuration (/ws)
│   │   └── resources/
│   │       ├── application.properties       # Server configuration (ports 8080 and 33000)
│   │       └── static/
│   │           ├── css/
│   │           │   └── style.css            # Dark-mode styling, layouts, and components
│   │           ├── js/
│   │           │   └── app.js               # Vanilla WebSocket client logic and DOM rendering
│   │           └── index.html               # Web GUI layout and modals
│   └── test/
│       └── java/utb/fai/
│           └── ImServerGuiApplicationTests.java # End-to-end integration and protocol tests
├── build.gradle                             # Gradle build configuration and dependencies
└── gradlew                                  # Gradle wrapper executable
```

## License

Made with Spring Boot by <a href="https://github.com/adamstefanik">Adam Samuel Štefánik</a>.
