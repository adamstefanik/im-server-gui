package utb.fai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketChatHandler extends TextWebSocketHandler {

    private final ActiveHandlers activeHandlers;
    private final ObjectMapper objectMapper;
    private final Map<String, WebSocketClientSession> sessions = new ConcurrentHashMap<>();

    public WebSocketChatHandler(ActiveHandlers activeHandlers, ObjectMapper objectMapper) {
        this.activeHandlers = activeHandlers;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        WebSocketClientSession clientSession = new WebSocketClientSession(session, objectMapper);
        sessions.put(session.getId(), clientSession);
        activeHandlers.add(clientSession);

        clientSession.sendJsonEvent("CONNECTED", Map.of(
                "sessionId", session.getId(),
                "prompt", "Enter your name:"
        ));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        WebSocketClientSession client = sessions.get(session.getId());
        if (client == null) {
            return;
        }

        String payload = message.getPayload().trim();
        if (payload.isEmpty()) {
            return;
        }

        // Try parsing as JSON first
        if (payload.startsWith("{") && payload.endsWith("}")) {
            try {
                JsonNode root = objectMapper.readTree(payload);
                String action = root.path("action").asText("");
                handleJsonAction(client, action, root);
                return;
            } catch (JsonProcessingException e) {
                // fallback to plain text if not valid JSON
            }
        }

        // Plain text handling (supports Task 4 CLI commands and input)
        handlePlainText(client, payload);
    }

    private void handleJsonAction(WebSocketClientSession client, String action, JsonNode root) {
        switch (action.toUpperCase()) {
            case "SET_NAME": {
                String name = root.path("name").asText("").trim();
                applySetName(client, name);
                break;
            }
            case "SEND_MESSAGE": {
                if (client.getUserName() == null) {
                    client.sendJsonEvent("ERROR", Map.of("message", "You must set your name first!"));
                    return;
                }
                String text = root.path("text").asText("").trim();
                String room = root.path("room").asText("").trim();
                if (!text.isEmpty()) {
                    if (text.startsWith("#")) {
                        handleCommand(client, text);
                    } else {
                        activeHandlers.sendMessageToSharedGroups(client, text, room.isEmpty() ? null : room);
                    }
                }
                break;
            }
            case "SEND_PRIVATE": {
                if (client.getUserName() == null) {
                    client.sendJsonEvent("ERROR", Map.of("message", "You must set your name first!"));
                    return;
                }
                String recipient = root.path("recipient").asText("").trim();
                String text = root.path("text").asText("").trim();
                if (!recipient.isEmpty() && !text.isEmpty()) {
                    boolean sent = activeHandlers.sendPrivateMessage(client, recipient, text);
                    if (!sent) {
                        client.sendJsonEvent("ERROR", Map.of("message", "User '" + recipient + "' not found or offline!"));
                    }
                }
                break;
            }
            case "JOIN_ROOM": {
                if (client.getUserName() == null) {
                    client.sendJsonEvent("ERROR", Map.of("message", "You must set your name first!"));
                    return;
                }
                String room = root.path("room").asText("").trim();
                if (!room.isEmpty() && !room.contains(" ")) {
                    client.addGroup(room);
                    client.sendJsonEvent("ROOM_JOINED", Map.of("room", room, "groups", client.getGroups()));
                    activeHandlers.broadcastSystemMessage("User '" + client.getUserName() + "' joined room #" + room, room);
                    activeHandlers.broadcastUserListUpdate();
                } else {
                    client.sendJsonEvent("ERROR", Map.of("message", "Invalid room name! Must not contain spaces."));
                }
                break;
            }
            case "LEAVE_ROOM": {
                if (client.getUserName() == null) {
                    return;
                }
                String room = root.path("room").asText("").trim();
                if (!room.isEmpty()) {
                    client.removeGroup(room);
                    client.sendJsonEvent("ROOM_LEFT", Map.of("room", room, "groups", client.getGroups()));
                    activeHandlers.broadcastSystemMessage("User '" + client.getUserName() + "' left room #" + room, room);
                    activeHandlers.broadcastUserListUpdate();
                }
                break;
            }
            default:
                client.sendJsonEvent("ERROR", Map.of("message", "Unknown action: " + action));
        }
    }

    private void handlePlainText(WebSocketClientSession client, String text) {
        if (client.getUserName() == null) {
            String desired = text;
            if (text.startsWith("#setMyName ")) {
                desired = text.substring("#setMyName ".length()).trim();
            }
            applySetName(client, desired);
            return;
        }

        if (text.startsWith("#")) {
            handleCommand(client, text);
        } else {
            activeHandlers.sendMessageToSharedGroups(client, text, null);
        }
    }

    private void applySetName(WebSocketClientSession client, String desiredName) {
        if (desiredName.isEmpty() || desiredName.contains(" ")) {
            client.sendJsonEvent("ERROR", Map.of("message", "Username must not be empty and cannot contain spaces!"));
            return;
        }
        if (!activeHandlers.isNameAvailable(desiredName, client)) {
            client.sendJsonEvent("ERROR", Map.of("message", "Username '" + desiredName + "' is already taken!"));
            return;
        }

        String oldName = client.getUserName();
        client.setUserName(desiredName);

        if (oldName == null) {
            client.addGroup("public");
            client.sendJsonEvent("NAME_SET", Map.of(
                    "name", desiredName,
                    "groups", client.getGroups()
            ));
            activeHandlers.broadcastSystemMessage("User '" + desiredName + "' connected via Web.", null);
        } else {
            client.sendJsonEvent("NAME_SET", Map.of(
                    "name", desiredName,
                    "groups", client.getGroups()
            ));
            activeHandlers.broadcastSystemMessage("User '" + oldName + "' changed name to '" + desiredName + "'.", null);
        }
        activeHandlers.broadcastUserListUpdate();
    }

    private void handleCommand(WebSocketClientSession client, String command) {
        if (command.startsWith("#setMyName ")) {
            String newName = command.substring("#setMyName ".length()).trim();
            applySetName(client, newName);
        } else if (command.startsWith("#sendPrivate ")) {
            String[] parts = command.split(" ", 3);
            if (parts.length >= 3) {
                String recipient = parts[1].trim();
                String privateMsg = parts[2];
                boolean sent = activeHandlers.sendPrivateMessage(client, recipient, privateMsg);
                if (!sent) {
                    client.sendJsonEvent("ERROR", Map.of("message", "User '" + recipient + "' not found!"));
                }
            } else {
                client.sendJsonEvent("ERROR", Map.of("message", "Usage: #sendPrivate <username> <message>"));
            }
        } else if (command.startsWith("#join ")) {
            String room = command.substring("#join ".length()).trim();
            if (!room.isEmpty() && !room.contains(" ")) {
                client.addGroup(room);
                client.sendJsonEvent("ROOM_JOINED", Map.of("room", room, "groups", client.getGroups()));
                activeHandlers.broadcastSystemMessage("User '" + client.getUserName() + "' joined room #" + room, room);
                activeHandlers.broadcastUserListUpdate();
            } else {
                client.sendJsonEvent("ERROR", Map.of("message", "Invalid room name! Must not contain spaces."));
            }
        } else if (command.startsWith("#leave ")) {
            String room = command.substring("#leave ".length()).trim();
            if (!room.isEmpty()) {
                client.removeGroup(room);
                client.sendJsonEvent("ROOM_LEFT", Map.of("room", room, "groups", client.getGroups()));
                activeHandlers.broadcastSystemMessage("User '" + client.getUserName() + "' left room #" + room, room);
                activeHandlers.broadcastUserListUpdate();
            }
        } else if (command.equals("#groups")) {
            client.sendJsonEvent("GROUPS_INFO", Map.of("groups", client.getGroups()));
        } else {
            client.sendJsonEvent("ERROR", Map.of("message", "Unknown command: " + command));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        WebSocketClientSession client = sessions.remove(session.getId());
        if (client != null) {
            activeHandlers.remove(client);
        }
    }
}
