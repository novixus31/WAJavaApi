package com.chatin.socket;

import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.listener.ConnectListener;
import com.corundumstudio.socketio.listener.DataListener;
import com.corundumstudio.socketio.listener.DisconnectListener;
import com.chatin.service.WhatsAppManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Socket.IO event handler - port of the socket.io event handlers in server.ts
 * Handles real-time communication between frontend and backend
 */
@Component
public class SocketIOEventHandler implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(SocketIOEventHandler.class);

    private final SocketIOServer server;
    private final WhatsAppManager whatsAppManager;

    public SocketIOEventHandler(SocketIOServer server, @Lazy WhatsAppManager whatsAppManager) {
        this.server = server;
        this.whatsAppManager = whatsAppManager;
    }

    @Override
    public void run(String... args) {
        // Connection listener
        server.addConnectListener(onConnect());

        // Disconnect listener
        server.addDisconnectListener(onDisconnect());

        // Admin connects
        server.addEventListener("admin_connect", Map.class, onAdminConnect());

        // User connects
        server.addEventListener("user_connect", Map.class, onUserConnect());

        // Join a chat room
        server.addEventListener("join_chat", Map.class, onJoinChat());

        // Leave a chat room
        server.addEventListener("leave_chat", Map.class, onLeaveChat());

        // Send message via socket
        server.addEventListener("send_message", Map.class, onSendMessage());

        // Mark chat as read
        server.addEventListener("mark_chat_read", Map.class, onMarkChatRead());

        // Request QR code
        server.addEventListener("request_qr", Map.class, onRequestQR());

        // Stop WhatsApp connection
        server.addEventListener("stop_whatsapp", Map.class, onStopWhatsApp());

        // Start the server
        server.start();
        logger.info("🔌 Socket.IO server started on port {}", server.getConfiguration().getPort());
    }

    private ConnectListener onConnect() {
        return client -> {
            logger.debug("Client connected: {}", client.getSessionId());
        };
    }

    private DisconnectListener onDisconnect() {
        return client -> {
            logger.debug("Client disconnected: {}", client.getSessionId());
        };
    }

    @SuppressWarnings("unchecked")
    private DataListener<Map> onAdminConnect() {
        return (client, data, ackSender) -> {
            String userId = (String) data.get("userId");
            String companyId = (String) data.get("companyId");
            logger.info("Admin connected: userId={}, companyId={}", userId, companyId);

            // Join user-specific room and company room
            if (userId != null) {
                client.joinRoom("user_" + userId);
            }
            if (companyId != null) {
                client.joinRoom("company_" + companyId);
            }
            client.joinRoom("admins");
        };
    }

    @SuppressWarnings("unchecked")
    private DataListener<Map> onUserConnect() {
        return (client, data, ackSender) -> {
            String userId = (String) data.get("userId");
            logger.info("User connected: userId={}", userId);

            if (userId != null) {
                client.joinRoom("user_" + userId);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private DataListener<Map> onJoinChat() {
        return (client, data, ackSender) -> {
            String accountId = (String) data.get("accountId");
            String remoteJid = (String) data.get("remoteJid");
            if (accountId != null) {
                client.joinRoom("account_" + accountId);
                if (remoteJid != null) {
                    client.joinRoom("chat_" + accountId + "_" + remoteJid);
                }
            }
        };
    }

    @SuppressWarnings("unchecked")
    private DataListener<Map> onLeaveChat() {
        return (client, data, ackSender) -> {
            String accountId = (String) data.get("accountId");
            String remoteJid = (String) data.get("remoteJid");
            if (accountId != null && remoteJid != null) {
                client.leaveRoom("chat_" + accountId + "_" + remoteJid);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private DataListener<Map> onSendMessage() {
        return (client, data, ackSender) -> {
            String accountId = (String) data.get("accountId");
            String remoteJid = (String) data.get("remoteJid");
            String text = (String) data.get("text");

            try {
                whatsAppManager.sendTextMessage(accountId, remoteJid, text, null);
            } catch (Exception e) {
                logger.error("Failed to send message via socket: {}", e.getMessage());
                client.sendEvent("error", Map.of("message", "Failed to send: " + e.getMessage()));
            }
        };
    }

    @SuppressWarnings("unchecked")
    private DataListener<Map> onMarkChatRead() {
        return (client, data, ackSender) -> {
            String accountId = (String) data.get("accountId");
            String remoteJid = (String) data.get("remoteJid");
            whatsAppManager.markChatRead(accountId, remoteJid);
        };
    }

    @SuppressWarnings("unchecked")
    private DataListener<Map> onRequestQR() {
        return (client, data, ackSender) -> {
            String accountId = (String) data.get("accountId");
            String userId = (String) data.get("userId");
            try {
                whatsAppManager.startConnection(accountId, userId);
            } catch (Exception e) {
                logger.error("Failed to start QR via socket: {}", e.getMessage());
                client.sendEvent("error", Map.of("message", "Failed to start connection"));
            }
        };
    }

    @SuppressWarnings("unchecked")
    private DataListener<Map> onStopWhatsApp() {
        return (client, data, ackSender) -> {
            String accountId = (String) data.get("accountId");
            whatsAppManager.stopConnection(accountId);
        };
    }

    /**
     * Emit an event to a specific room
     */
    public void emitToRoom(String room, String event, Object data) {
        server.getRoomOperations(room).sendEvent(event, data);
    }

    /**
     * Emit an event to all clients
     */
    public void emitToAll(String event, Object data) {
        server.getBroadcastOperations().sendEvent(event, data);
    }
}
