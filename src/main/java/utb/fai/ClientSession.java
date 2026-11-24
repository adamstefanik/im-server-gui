package utb.fai;

import java.util.Set;

/**
 * Common interface representing any connected client session (Web or TCP/Telnet).
 */
public interface ClientSession {

    /**
     * Unique identifier for the client (e.g. IP:port or WebSocket session id).
     */
    String getClientID();

    /**
     * User name of the client, or null if not yet authenticated.
     */
    String getUserName();

    /**
     * Sets the user name for this client.
     */
    void setUserName(String name);

    /**
     * Returns a thread-safe set of discussion groups the client is currently in.
     */
    Set<String> getGroups();

    /**
     * Adds client to a discussion room.
     */
    void addGroup(String group);

    /**
     * Removes client from a discussion room.
     */
    void removeGroup(String group);

    /**
     * Checks if client is member of given group.
     */
    boolean hasGroup(String group);

    /**
     * Sends a raw message to client (used by TCP clients or fallback).
     */
    void sendRawMessage(String message);

    /**
     * Sends a structured JSON event to client (used by Web clients).
     */
    void sendJsonEvent(String type, Object payload);

    /**
     * Type of client: "WEB" or "TELNET".
     */
    String getClientType();

    /**
     * Checks if connection is currently active.
     */
    boolean isConnected();

    /**
     * Closes client connection.
     */
    void close();
}
