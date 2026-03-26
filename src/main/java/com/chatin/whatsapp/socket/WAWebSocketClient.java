package com.chatin.whatsapp.socket;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * WebSocket client for WhatsApp - Java port of baileys Socket/Client
 * Wraps Java-WebSocket library with event emitter pattern
 */
public class WAWebSocketClient extends WebSocketClient {

    private static final Logger logger = LoggerFactory.getLogger(WAWebSocketClient.class);

    private final Map<String, List<Consumer<Object>>> eventListeners = new ConcurrentHashMap<>();
    private volatile boolean connected = false;

    public WAWebSocketClient(URI serverUri) {
        super(serverUri);
        this.addHeader("Origin", "https://web.whatsapp.com");
        this.addHeader("User-Agent", "Mozilla/5.0");
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        connected = true;
        logger.info("WebSocket connected to WhatsApp");
        emit("open", null);
    }

    @Override
    public void onMessage(String message) {
        // WhatsApp uses binary messages, text messages are unusual
        logger.debug("Text message received (unusual): {}", message);
    }

    @Override
    public void onMessage(ByteBuffer bytes) {
        byte[] data = new byte[bytes.remaining()];
        bytes.get(data);
        emit("message", data);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        connected = false;
        logger.info("WebSocket closed: code={}, reason={}, remote={}", code, reason, remote);
        emit("close", Map.of("code", code, "reason", reason != null ? reason : ""));
    }

    @Override
    public void onError(Exception ex) {
        logger.error("WebSocket error: {}", ex.getMessage());
        emit("error", ex);
    }

    /**
     * Register an event listener
     */
    public void on(String event, Consumer<Object> listener) {
        eventListeners.computeIfAbsent(event, k -> new ArrayList<>()).add(listener);
    }

    /**
     * Remove an event listener
     */
    public void off(String event, Consumer<Object> listener) {
        List<Consumer<Object>> listeners = eventListeners.get(event);
        if (listeners != null) {
            listeners.remove(listener);
        }
    }

    /**
     * Remove all listeners for an event
     */
    public void removeAllListeners(String event) {
        eventListeners.remove(event);
    }

    /**
     * Emit an event
     */
    public boolean emit(String event, Object data) {
        List<Consumer<Object>> listeners = eventListeners.get(event);
        if (listeners != null && !listeners.isEmpty()) {
            for (Consumer<Object> listener : new ArrayList<>(listeners)) {
                try {
                    listener.accept(data);
                } catch (Exception e) {
                    logger.error("Error in event listener for '{}': {}", event, e.getMessage());
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Send binary data
     */
    public void sendBinary(byte[] data) {
        if (connected) {
            send(data);
        } else {
            logger.warn("Attempted to send while disconnected");
        }
    }

    public boolean isConnected() {
        return connected && isOpen();
    }
}
