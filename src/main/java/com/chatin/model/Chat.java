package com.chatin.model;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Data
@Document(collection = "chats")
@CompoundIndex(name = "account_remoteJid_idx", def = "{'accountId': 1, 'remoteJid': 1}", unique = true)
public class Chat {

    @Id
    private String id;

    @Indexed
    private String accountId;

    @Indexed
    private String remoteJid;

    private String name;
    private String customTitle;
    private String profilePic;
    private Boolean isGroup;

    // Last message info
    private String lastMessageContent;
    private String lastMessageType;
    private Instant lastMessageTimestamp;
    private Boolean lastMessageFromMe;
    private String lastMessageSenderName;

    // Unread counts - per user
    private Map<String, Integer> unreadCounts;

    // Chat settings
    private Boolean isPinned;
    private Boolean isMuted;
    private Boolean isArchived;
    private String muteExpiration;

    // WhatsApp metadata
    private String conversationTimestamp;
    private Integer messageCount;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
