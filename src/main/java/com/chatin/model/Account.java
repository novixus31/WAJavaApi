package com.chatin.model;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Data
@Document(collection = "accounts")
@CompoundIndex(name = "company_order_idx", def = "{'companyId': 1, 'companyOrder': 1}")
public class Account {

    @Id
    private String id;

    @Indexed
    private String name;

    private String type; // "whatsapp" or "telegram"
    private String status; // "disconnected", "connecting", "connected", "qr_ready"
    private String phoneNumber;

    @Indexed
    private String companyId;

    private String companyName;
    private Integer companyOrder;

    // WhatsApp specific
    private String waName;
    private String waPlatform;
    private String waProfilePic;
    private String waJid;

    // Telegram specific
    private String telegramBotToken;
    private String telegramBotUsername;

    // Connection metadata
    private Instant lastConnected;
    private Instant lastDisconnected;
    private String disconnectReason;
    private Boolean isActive;

    // Stats
    private Long totalMessages;
    private Long totalChats;
    private Long totalMediaMessages;
    private Long lastDayMessages;

    // QR Code
    private String qrCode;
    private Instant qrGeneratedAt;

    // Session info
    private Boolean hasSession;

    // WebSocket session
    private String socketId;

    // Tags/Labels
    private List<String> tags;
    private String notes;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
