package utb.fai;

import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;

public class TcpClientSession implements ClientSession {

    final Socket mySocket;
    final String clientID;
    private String userName = null;
    private final Set<String> groups = new CopyOnWriteArraySet<>();
    final ActiveHandlers activeHandlers;

    final ArrayBlockingQueue<String> messages = new ArrayBlockingQueue<>(100);
    final CountDownLatch startSignal = new CountDownLatch(2);

    final OutputHandler outputHandler = new OutputHandler();
    final InputHandler inputHandler = new InputHandler();

    volatile boolean inputFinished = false;

    public TcpClientSession(Socket mySocket, ActiveHandlers activeHandlers) {
        this.mySocket = mySocket;
        this.clientID = "tcp:" + mySocket.getInetAddress().toString() + ":" + mySocket.getPort();
        this.activeHandlers = activeHandlers;
    }

    @Override
    public String getClientID() {
        return clientID;
    }

    @Override
    public String getUserName() {
        return userName;
    }

    @Override
    public void setUserName(String name) {
        this.userName = name;
    }

    @Override
    public Set<String> getGroups() {
        return groups;
    }

    @Override
    public void addGroup(String group) {
        if (group != null && !group.trim().isEmpty()) {
            groups.add(group.trim());
        }
    }

    @Override
    public void removeGroup(String group) {
        if (group != null) {
            groups.remove(group.trim());
        }
    }

    @Override
    public boolean hasGroup(String group) {
        return groups.contains(group);
    }

    @Override
    public void sendRawMessage(String message) {
        if (!messages.offer(message)) {
            System.err.printf("Client %s message queue is full, dropping message!\n", clientID);
        }
    }

    @Override
    public void sendJsonEvent(String type, Object payload) {
        // TCP clients receive raw string messages. For system notifications or events:
        if ("SYSTEM".equalsIgnoreCase(type) && payload instanceof Map) {
            Object text = ((Map<?, ?>) payload).get("text");
            if (text != null) {
                sendRawMessage("[SYSTEM] >> " + text);
            }
        }
    }

    @Override
    public String getClientType() {
        return "TELNET";
    }

    @Override
    public boolean isConnected() {
        return !mySocket.isClosed() && mySocket.isConnected();
    }

    @Override
    public void close() {
        inputFinished = true;
        try {
            if (!mySocket.isClosed()) {
                mySocket.close();
            }
        } catch (IOException e) {
            // ignore
        }
    }

    public Runnable getInputTask() {
        return inputHandler;
    }

    public Runnable getOutputTask() {
        return outputHandler;
    }

    class OutputHandler implements Runnable {
        public void run() {
            OutputStreamWriter writer;
            try {
                startSignal.countDown();
                startSignal.await();
                writer = new OutputStreamWriter(mySocket.getOutputStream(), "UTF-8");
                writer.write("Enter your name:\r\n");
                writer.flush();
                while (!inputFinished) {
                    String m = messages.take();
                    if (inputFinished) {
                        break;
                    }
                    writer.write(m + "\r\n");
                    writer.flush();
                }
            } catch (IOException | InterruptedException e) {
                // socket closed or interrupted
            } finally {
                close();
            }
        }
    }

    class InputHandler implements Runnable {
        public void run() {
            try {
                startSignal.countDown();
                startSignal.await();
                activeHandlers.add(TcpClientSession.this);
                BufferedReader reader = new BufferedReader(new InputStreamReader(mySocket.getInputStream(), "UTF-8"));
                String request;
                while ((request = reader.readLine()) != null) {
                    request = request.trim();
                    if (request.isEmpty()) {
                        continue;
                    }

                    if (userName == null) {
                        String desiredName = request;
                        if (request.startsWith("#setMyName ")) {
                            desiredName = request.substring("#setMyName ".length()).trim();
                        }
                        if (!desiredName.isEmpty() && !desiredName.contains(" ")
                                && activeHandlers.isNameAvailable(desiredName, TcpClientSession.this)) {
                            userName = desiredName;
                            groups.add("public");
                            activeHandlers.broadcastSystemMessage("User '" + userName + "' joined via Telnet.", null);
                            activeHandlers.broadcastUserListUpdate();
                        }
                        continue;
                    }

                    if (request.startsWith("#")) {
                        handleCommand(request);
                    } else {
                        activeHandlers.sendMessageToSharedGroups(TcpClientSession.this, request, null);
                    }
                }
            } catch (IOException | InterruptedException e) {
                // disconnected
            } finally {
                inputFinished = true;
                messages.offer(""); // unblock output handler
                activeHandlers.remove(TcpClientSession.this);
                close();
            }
        }

        private void handleCommand(String command) {
            if (command.startsWith("#setMyName ")) {
                String newName = command.substring("#setMyName ".length()).trim();
                if (!newName.isEmpty() && !newName.contains(" ")
                        && activeHandlers.isNameAvailable(newName, TcpClientSession.this)) {
                    String oldName = userName;
                    userName = newName;
                    activeHandlers.broadcastSystemMessage("User '" + oldName + "' changed name to '" + newName + "'.", null);
                    activeHandlers.broadcastUserListUpdate();
                }
            } else if (command.startsWith("#sendPrivate ")) {
                String[] parts = command.split(" ", 3);
                if (parts.length >= 3) {
                    String recipient = parts[1].trim();
                    String privateMsg = parts[2];
                    activeHandlers.sendPrivateMessage(TcpClientSession.this, recipient, privateMsg);
                }
            } else if (command.startsWith("#join ")) {
                String room = command.substring("#join ".length()).trim();
                if (!room.isEmpty() && !room.contains(" ")) {
                    groups.add(room);
                    activeHandlers.broadcastUserListUpdate();
                }
            } else if (command.startsWith("#leave ")) {
                String room = command.substring("#leave ".length()).trim();
                if (!room.isEmpty()) {
                    groups.remove(room);
                    activeHandlers.broadcastUserListUpdate();
                }
            } else if (command.equals("#groups")) {
                String groupList = String.join(",", groups);
                sendRawMessage(groupList);
            }
        }
    }
}
