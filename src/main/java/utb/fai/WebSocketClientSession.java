package utb.fai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

public class WebSocketClientSession implements ClientSession {

    private final WebSocketSession session;
    private final String clientID;
    private String userName = null;
    private final Set<String> groups = new CopyOnWriteArraySet<>();
    private final ObjectMapper objectMapper;

    public WebSocketClientSession(WebSocketSession session, ObjectMapper objectMapper) {
        this.session = session;
        this.clientID = "ws:" + session.getId();
        this.objectMapper = objectMapper;
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
        sendJsonEvent("RAW", message);
    }

    @Override
    public void sendJsonEvent(String type, Object payload) {
        if (!session.isOpen()) {
            return;
        }
        try {
            Map<String, Object> envelope = new HashMap<>();
            envelope.put("type", type);
            envelope.put("data", payload);

            String json = objectMapper.writeValueAsString(envelope);
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(json));
                }
            }
        } catch (IOException e) {
            System.err.println("Error sending WebSocket message to " + clientID + ": " + e.getMessage());
        }
    }

    @Override
    public String getClientType() {
        return "WEB";
    }

    @Override
    public boolean isConnected() {
        return session.isOpen();
    }

    @Override
    public void close() {
        try {
            if (session.isOpen()) {
                session.close();
            }
        } catch (IOException e) {
            // ignore
        }
    }
}
