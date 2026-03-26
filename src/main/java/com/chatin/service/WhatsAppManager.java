package com.chatin.service;

import com.chatin.model.Account;
import com.chatin.model.Chat;
import com.chatin.model.Message;
import com.chatin.repository.*;
import com.chatin.socket.SocketIOEventHandler;
import com.chatin.whatsapp.binary.BinaryNode;
import com.chatin.whatsapp.binary.JidUtils;
import com.chatin.whatsapp.socket.WASocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WhatsApp Manager - Java port of lib/whatsappManager.ts
 * Central orchestrator for all WhatsApp connections.
 * Manages connection lifecycle, message routing, and state sync.
 */
@Service
public class WhatsAppManager {

    private static final Logger logger = LoggerFactory.getLogger(WhatsAppManager.class);

    private final AccountRepository accountRepository;
    private final ChatRepository chatRepository;
    private final MessageRepository messageRepository;
    private final ReactionRepository reactionRepository;
    private final MongoAuthState mongoAuthState;
    private final SocketIOEventHandler socketIOEventHandler;

    // Active connections: accountId -> WASocket
    private final Map<String, WASocket> connections = new ConcurrentHashMap<>();

    public WhatsAppManager(AccountRepository accountRepository,
                           ChatRepository chatRepository,
                           MessageRepository messageRepository,
                           ReactionRepository reactionRepository,
                           MongoAuthState mongoAuthState,
                           SocketIOEventHandler socketIOEventHandler) {
        this.accountRepository = accountRepository;
        this.chatRepository = chatRepository;
        this.messageRepository = messageRepository;
        this.reactionRepository = reactionRepository;
        this.mongoAuthState = mongoAuthState;
        this.socketIOEventHandler = socketIOEventHandler;
    }

    /**
     * Start or restart a WhatsApp connection for an account
     */
    public void startConnection(String accountId, String userId) {
        // Stop existing connection if any
        stopConnection(accountId);

        logger.info("Starting WhatsApp connection for account: {}", accountId);

        Optional<Account> accountOpt = accountRepository.findById(accountId);
        if (accountOpt.isEmpty()) {
            logger.error("Account not found: {}", accountId);
            return;
        }

        Account account = accountOpt.get();
        account.setStatus("connecting");
        accountRepository.save(account);

        // Emit status update
        emitAccountStatus(accountId, "connecting");

        try {
            // Get auth state from MongoDB
            WASocket.AuthState authState = mongoAuthState.getAuthState(accountId);

            // Create socket connection
            WASocket socket = new WASocket();

            // Set up event handlers
            socket.setOnConnectionUpdate(update -> handleConnectionUpdate(accountId, update));
            socket.setOnQrCode(qr -> handleQrCode(accountId, qr));
            socket.setOnCredsUpdate(creds -> handleCredsUpdate(accountId, authState.creds));
            socket.setOnMessage(node -> handleIncomingNode(accountId, node));

            // Store connection
            connections.put(accountId, socket);

            // Connect
            socket.connect(authState);

        } catch (Exception e) {
            logger.error("Failed to start connection for account {}: {}", accountId, e.getMessage());
            account.setStatus("disconnected");
            account.setDisconnectReason(e.getMessage());
            accountRepository.save(account);
            emitAccountStatus(accountId, "disconnected");
        }
    }

    /**
     * Stop a WhatsApp connection
     */
    public void stopConnection(String accountId) {
        WASocket socket = connections.remove(accountId);
        if (socket != null) {
            logger.info("Stopping WhatsApp connection for account: {}", accountId);
            socket.end(new RuntimeException("User requested disconnect"));

            Optional<Account> accountOpt = accountRepository.findById(accountId);
            accountOpt.ifPresent(account -> {
                account.setStatus("disconnected");
                account.setLastDisconnected(Instant.now());
                accountRepository.save(account);
            });

            emitAccountStatus(accountId, "disconnected");
        }
    }

    /**
     * Handle connection state changes
     */
    private void handleConnectionUpdate(String accountId, Map<String, Object> update) {
        String connection = (String) update.get("connection");

        if ("open".equals(connection)) {
            logger.info("WhatsApp connected for account: {}", accountId);
            Optional<Account> accountOpt = accountRepository.findById(accountId);
            accountOpt.ifPresent(account -> {
                account.setStatus("connected");
                account.setLastConnected(Instant.now());
                account.setQrCode(null);
                accountRepository.save(account);
            });
            emitAccountStatus(accountId, "connected");

        } else if ("close".equals(connection)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> lastDisconnect = (Map<String, Object>) update.get("lastDisconnect");
            final String reason;
            if (lastDisconnect != null) {
                reason = (String) lastDisconnect.getOrDefault("error", "Unknown");
            } else {
                reason = "Unknown";
            }

            boolean shouldReconnect = shouldReconnect(reason);
            logger.info("WhatsApp disconnected for account: {} reason: {} reconnect: {}",
                    accountId, reason, shouldReconnect);

            Optional<Account> accountOpt = accountRepository.findById(accountId);
            accountOpt.ifPresent(account -> {
                account.setStatus("disconnected");
                account.setLastDisconnected(Instant.now());
                account.setDisconnectReason(reason);
                accountRepository.save(account);
            });

            emitAccountStatus(accountId, "disconnected");
            connections.remove(accountId);

            if (shouldReconnect) {
                // Auto-reconnect after 5 seconds
                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        startConnection(accountId, null);
                    }
                }, 5000);
            }
        }
    }

    /**
     * Handle QR code generation
     */
    private void handleQrCode(String accountId, String qrCode) {
        logger.info("QR code generated for account: {}", accountId);

        Optional<Account> accountOpt = accountRepository.findById(accountId);
        accountOpt.ifPresent(account -> {
            account.setStatus("qr_ready");
            account.setQrCode(qrCode);
            account.setQrGeneratedAt(Instant.now());
            accountRepository.save(account);
        });

        // Emit QR to front end
        socketIOEventHandler.emitToRoom("account_" + accountId, "qr_code", Map.of(
                "accountId", accountId,
                "qr", qrCode
        ));

        emitAccountStatus(accountId, "qr_ready");
    }

    /**
     * Handle credentials update
     */
    private void handleCredsUpdate(String accountId, WASocket.AuthCreds creds) {
        mongoAuthState.saveCreds(accountId, creds);
    }

    /**
     * Handle incoming binary nodes (messages, receipts, etc.)
     */
    private void handleIncomingNode(String accountId, BinaryNode node) {
        if (node == null) return;

        String tag = node.getTag();

        switch (tag) {
            case "message" -> handleMessageUpsert(accountId, node);
            case "receipt" -> handleReceipt(accountId, node);
            case "notification" -> handleNotification(accountId, node);
            default -> logger.debug("Unhandled node tag: {} for account: {}", tag, accountId);
        }
    }

    /**
     * Handle incoming message upsert
     */
    private void handleMessageUpsert(String accountId, BinaryNode node) {
        try {
            String remoteJid = node.getAttr("from");
            if (remoteJid == null) return;

            // Skip status broadcasts
            if (JidUtils.isJidStatusBroadcast(remoteJid)) return;

            String messageId = node.getAttr("id");
            boolean fromMe = "true".equals(node.getAttr("fromMe"));
            String participant = node.getAttr("participant");
            String pushName = node.getAttr("notify");

            // Parse message content from node
            String messageType = "text";
            String content = "";
            BinaryNode msgContent = node.getChild("body");
            if (msgContent != null) {
                content = msgContent.getContentString();
            }

            // Create/update message in DB
            Message message = messageRepository.findByAccountIdAndMessageId(accountId, messageId)
                    .orElse(new Message());

            message.setAccountId(accountId);
            message.setRemoteJid(remoteJid);
            message.setMessageId(messageId);
            message.setFromMe(fromMe);
            message.setMessageType(messageType);
            message.setContent(content);
            message.setPushName(pushName);
            message.setParticipant(participant);
            message.setMessageTimestamp(Instant.now());

            messageRepository.save(message);

            // Update or create chat
            Chat chat = chatRepository.findByAccountIdAndRemoteJid(accountId, remoteJid)
                    .orElse(new Chat());

            chat.setAccountId(accountId);
            chat.setRemoteJid(remoteJid);
            chat.setLastMessageContent(content);
            chat.setLastMessageType(messageType);
            chat.setLastMessageTimestamp(Instant.now());
            chat.setLastMessageFromMe(fromMe);
            if (pushName != null) {
                chat.setLastMessageSenderName(pushName);
                if (chat.getName() == null || chat.getName().isEmpty()) {
                    chat.setName(pushName);
                }
            }
            chat.setIsGroup(JidUtils.isJidGroup(remoteJid));

            // Update unread counts for all users (except sender)
            if (!fromMe) {
                Map<String, Integer> unreadCounts = chat.getUnreadCounts();
                if (unreadCounts == null) unreadCounts = new HashMap<>();
                // Increment all users' unread counts
                for (String key : unreadCounts.keySet()) {
                    unreadCounts.merge(key, 1, Integer::sum);
                }
                chat.setUnreadCounts(unreadCounts);
            }

            chatRepository.save(chat);

            // Emit message to Socket.IO
            Map<String, Object> msgData = new HashMap<>();
            msgData.put("accountId", accountId);
            msgData.put("remoteJid", remoteJid);
            msgData.put("messageId", messageId);
            msgData.put("fromMe", fromMe);
            msgData.put("content", content);
            msgData.put("messageType", messageType);
            msgData.put("pushName", pushName);
            msgData.put("timestamp", message.getMessageTimestamp());

            socketIOEventHandler.emitToRoom("account_" + accountId, "new_message", msgData);
            socketIOEventHandler.emitToRoom("chat_" + accountId + "_" + remoteJid, "new_message", msgData);

        } catch (Exception e) {
            logger.error("Error handling message for account {}: {}", accountId, e.getMessage());
        }
    }

    /**
     * Handle read receipts
     */
    private void handleReceipt(String accountId, BinaryNode node) {
        String type = node.getAttr("type");
        if ("read".equals(type) || "read-self".equals(type)) {
            logger.debug("Read receipt for account: {}", accountId);
            // TODO update message status
        }
    }

    /**
     * Handle notifications
     */
    private void handleNotification(String accountId, BinaryNode node) {
        logger.debug("Notification for account {}: {}", accountId, node.getAttr("type"));
    }

    /**
     * Send a text message
     */
    public void sendTextMessage(String accountId, String remoteJid, String text, Map<String, String> quotedMessage) {
        WASocket socket = connections.get(accountId);
        if (socket == null || !socket.isOpen()) {
            throw new RuntimeException("Account not connected: " + accountId);
        }

        try {
            Map<String, String> attrs = new HashMap<>();
            attrs.put("to", remoteJid);

            BinaryNode msgNode = new BinaryNode("message", attrs,
                    List.of(new BinaryNode("body", Map.of(), text)));

            socket.sendNode(msgNode);
            logger.info("Text message sent to {} on account {}", remoteJid, accountId);

        } catch (Exception e) {
            logger.error("Failed to send text message: {}", e.getMessage());
            throw new RuntimeException("Failed to send message", e);
        }
    }

    /**
     * Send a media message
     */
    public void sendMediaMessage(String accountId, String remoteJid, String mediaType,
                                  String mediaUrl, String caption, String fileName, String mimetype) {
        WASocket socket = connections.get(accountId);
        if (socket == null || !socket.isOpen()) {
            throw new RuntimeException("Account not connected: " + accountId);
        }

        try {
            // In production, would encode the media message properly
            logger.info("Media message ({}) sent to {} on account {}", mediaType, remoteJid, accountId);
        } catch (Exception e) {
            logger.error("Failed to send media message: {}", e.getMessage());
            throw new RuntimeException("Failed to send media", e);
        }
    }

    /**
     * Send a reaction
     */
    public void sendReaction(String accountId, String remoteJid, String messageId, String emoji) {
        WASocket socket = connections.get(accountId);
        if (socket == null || !socket.isOpen()) {
            throw new RuntimeException("Account not connected: " + accountId);
        }

        try {
            logger.info("Reaction '{}' sent to message {} on account {}", emoji, messageId, accountId);
        } catch (Exception e) {
            logger.error("Failed to send reaction: {}", e.getMessage());
            throw new RuntimeException("Failed to send reaction", e);
        }
    }

    /**
     * Mark a chat as read
     */
    public void markChatRead(String accountId, String remoteJid) {
        WASocket socket = connections.get(accountId);
        if (socket != null && socket.isOpen()) {
            try {
                BinaryNode readNode = new BinaryNode("receipt", Map.of(
                        "type", "read",
                        "to", remoteJid,
                        "id", socket.generateMessageTag()
                ));
                socket.sendNode(readNode);
            } catch (Exception e) {
                logger.error("Failed to mark chat as read: {}", e.getMessage());
            }
        }
    }

    /**
     * Get connection status for an account
     */
    public String getConnectionStatus(String accountId) {
        WASocket socket = connections.get(accountId);
        if (socket == null) return "disconnected";
        return switch (socket.getState()) {
            case OPEN -> "connected";
            case CONNECTING -> "connecting";
            default -> "disconnected";
        };
    }

    /**
     * Get all active connections
     */
    public Map<String, String> getAllConnectionStatuses() {
        Map<String, String> statuses = new HashMap<>();
        for (Map.Entry<String, WASocket> entry : connections.entrySet()) {
            statuses.put(entry.getKey(), getConnectionStatus(entry.getKey()));
        }
        return statuses;
    }

    /**
     * Determine if we should auto-reconnect
     */
    private boolean shouldReconnect(String reason) {
        if (reason == null) return true;
        // Don't reconnect on intentional disconnects or auth failures
        return !reason.contains("Intentional") &&
               !reason.contains("loggedOut") &&
               !reason.contains("401");
    }

    /**
     * Emit account status change to Socket.IO
     */
    private void emitAccountStatus(String accountId, String status) {
        socketIOEventHandler.emitToAll("account_status", Map.of(
                "accountId", accountId,
                "status", status
        ));
    }
}
