package utb.fai;

import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

@Service
public class ActiveHandlers {

    private final Set<ClientSession> activeSessions = new CopyOnWriteArraySet<>();
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * Adds client session to the active set.
     */
    public synchronized boolean add(ClientSession session) {
        boolean added = activeSessions.add(session);
        if (added && session.getUserName() != null) {
            broadcastUserListUpdate();
        }
        return added;
    }

    /**
     * Removes client session from active set.
     */
    public synchronized boolean remove(ClientSession session) {
        boolean removed = activeSessions.remove(session);
        if (removed && session.getUserName() != null) {
            broadcastSystemMessage("User '" + session.getUserName() + "' has disconnected.", null);
            broadcastUserListUpdate();
        }
        return removed;
    }

    /**
     * Validates if a user name is valid and unique among active clients.
     */
    public synchronized boolean isNameAvailable(String name, ClientSession currentSession) {
        if (name == null || name.trim().isEmpty() || name.contains(" ")) {
            return false;
        }
        for (ClientSession session : activeSessions) {
            if (session != currentSession && name.equals(session.getUserName())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Sends message to all clients sharing at least one discussion group with the sender,
     * or specifically to targetRoom if provided.
     */
    public synchronized void sendMessageToSharedGroups(ClientSession sender, String rawMessage, String targetRoom) {
        String senderName = sender.getUserName();
        String formatted = "[" + senderName + "] >> " + rawMessage;
        System.out.println(formatted);

        String timestamp = LocalTime.now().format(timeFormatter);
        Set<String> senderGroups = sender.getGroups();

        // Effective room to associate with message (for UI categorization)
        String roomForUi = targetRoom;
        if (roomForUi == null || roomForUi.isEmpty()) {
            roomForUi = senderGroups.contains("public") ? "public" : (senderGroups.isEmpty() ? "general" : senderGroups.iterator().next());
        }

        Map<String, Object> chatPayload = new HashMap<>();
        chatPayload.put("sender", senderName);
        chatPayload.put("text", rawMessage);
        chatPayload.put("room", roomForUi);
        chatPayload.put("raw", formatted);
        chatPayload.put("timestamp", timestamp);
        chatPayload.put("clientType", sender.getClientType());

        for (ClientSession recipient : activeSessions) {
            if (recipient.getUserName() != null) {
                boolean shouldDeliver = false;
                if (targetRoom != null && !targetRoom.isEmpty()) {
                    shouldDeliver = recipient.hasGroup(targetRoom);
                } else {
                    shouldDeliver = sharesGroup(senderGroups, recipient.getGroups());
                }

                if (shouldDeliver) {
                    if ("WEB".equalsIgnoreCase(recipient.getClientType())) {
                        chatPayload.put("isSelf", recipient == sender);
                        recipient.sendJsonEvent("CHAT", chatPayload);
                    } else {
                        // Telnet clients do not receive their own echo
                        if (recipient != sender) {
                            recipient.sendRawMessage(formatted);
                        }
                    }
                }
            }
        }
    }

    /**
     * Sends a private message to a specific user by user name.
     */
    public synchronized boolean sendPrivateMessage(ClientSession sender, String recipientName, String message) {
        if (recipientName == null || recipientName.trim().isEmpty()) {
            return false;
        }
        ClientSession recipient = null;
        for (ClientSession session : activeSessions) {
            if (session.getUserName() != null && recipientName.equals(session.getUserName())) {
                recipient = session;
                break;
            }
        }

        if (recipient == null) {
            return false;
        }

        String formatted = "[" + sender.getUserName() + "] >> " + message;
        String timestamp = LocalTime.now().format(timeFormatter);

        Map<String, Object> recipientPayload = new HashMap<>();
        recipientPayload.put("sender", sender.getUserName());
        recipientPayload.put("recipient", recipientName);
        recipientPayload.put("text", message);
        recipientPayload.put("raw", formatted);
        recipientPayload.put("timestamp", timestamp);
        recipientPayload.put("isPrivate", true);
        recipientPayload.put("isOutgoing", false);

        if ("WEB".equalsIgnoreCase(recipient.getClientType())) {
            recipient.sendJsonEvent("PRIVATE", recipientPayload);
        } else {
            recipient.sendRawMessage(formatted);
        }

        // Echo back to sender if sender is WEB client so UI displays the sent private message
        if ("WEB".equalsIgnoreCase(sender.getClientType())) {
            Map<String, Object> senderPayload = new HashMap<>(recipientPayload);
            senderPayload.put("isOutgoing", true);
            sender.sendJsonEvent("PRIVATE", senderPayload);
        }

        return true;
    }

    /**
     * Sends a system broadcast to all active clients or to members of a target room.
     */
    public synchronized void broadcastSystemMessage(String text, String targetRoom) {
        String timestamp = LocalTime.now().format(timeFormatter);
        Map<String, Object> payload = new HashMap<>();
        payload.put("text", text);
        payload.put("timestamp", timestamp);
        payload.put("room", targetRoom != null ? targetRoom : "system");

        for (ClientSession session : activeSessions) {
            if (session.getUserName() != null) {
                if (targetRoom == null || session.hasGroup(targetRoom)) {
                    if ("WEB".equalsIgnoreCase(session.getClientType())) {
                        session.sendJsonEvent("SYSTEM", payload);
                    }
                }
            }
        }
    }

    /**
     * Broadcasts updated online user list and active rooms to all Web clients.
     */
    public synchronized void broadcastUserListUpdate() {
        List<Map<String, Object>> users = new ArrayList<>();
        Set<String> allRooms = new TreeSet<>();
        allRooms.add("public");

        for (ClientSession session : activeSessions) {
            if (session.getUserName() != null) {
                Map<String, Object> userMap = new HashMap<>();
                userMap.put("name", session.getUserName());
                userMap.put("clientType", session.getClientType());
                userMap.put("groups", new ArrayList<>(session.getGroups()));
                users.add(userMap);
                allRooms.addAll(session.getGroups());
            }
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("users", users);
        payload.put("rooms", new ArrayList<>(allRooms));

        for (ClientSession session : activeSessions) {
            if ("WEB".equalsIgnoreCase(session.getClientType())) {
                session.sendJsonEvent("USER_LIST", payload);
            }
        }
    }

    /**
     * Helper to verify if two sets have any shared room.
     */
    private boolean sharesGroup(Set<String> set1, Set<String> set2) {
        for (String g : set1) {
            if (set2.contains(g)) {
                return true;
            }
        }
        return false;
    }

    public synchronized Set<ClientSession> getActiveSessions() {
        return Collections.unmodifiableSet(activeSessions);
    }
}
