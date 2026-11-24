package utb.fai;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
public class TcpServerService implements CommandLineRunner {

    private final ActiveHandlers activeHandlers;

    @Value("${im.server.tcp-enabled:true}")
    private boolean tcpEnabled;

    @Value("${im.server.tcp-port:33000}")
    private int tcpPort;

    @Value("${im.server.max-connections:100}")
    private int maxConnections;

    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private Thread serverThread;
    private volatile boolean running = false;

    public TcpServerService(ActiveHandlers activeHandlers) {
        this.activeHandlers = activeHandlers;
    }

    @Override
    public void run(String... args) {
        if (!tcpEnabled) {
            System.out.println("TCP Telnet server is disabled by configuration.");
            return;
        }

        threadPool = Executors.newFixedThreadPool(2 * maxConnections);
        running = true;

        serverThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(tcpPort);
                System.out.printf("IM TCP server listening on port %d (max connections: %d)...\n", tcpPort, maxConnections);

                while (running && !serverSocket.isClosed()) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        clientSocket.setKeepAlive(true);
                        TcpClientSession session = new TcpClientSession(clientSocket, activeHandlers);
                        threadPool.execute(session.getInputTask());
                        threadPool.execute(session.getOutputTask());
                    } catch (IOException e) {
                        if (running) {
                            System.err.println("TCP accept error: " + e.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                System.err.printf("Could not start TCP server on port %d: %s (Web GUI is still fully operational)\n",
                        tcpPort, e.getMessage());
            }
        }, "TCP-Server-Thread");

        serverThread.setDaemon(true);
        serverThread.start();
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                // ignore
            }
        }
        if (threadPool != null) {
            threadPool.shutdown();
            try {
                if (!threadPool.awaitTermination(2, TimeUnit.SECONDS)) {
                    threadPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                threadPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
