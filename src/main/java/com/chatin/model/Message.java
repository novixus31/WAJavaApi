package com.chatin.model;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Document(collection = "messages")
@CompoundIndexes({
    @CompoundIndex(name = "account_remoteJid_timestamp_idx", def = "{'accountId': 1, 'remoteJid': 1, 'messageTimestamp': -1}"),
    @CompoundIndex(name = "account_messageId_idx", def = "{'accountId': 1, 'messageId': 1}", unique = true)
})
public class Message {

    @Id
    private String id;

    @Indexed
    private String accountId;

    @Indexed
    private String remoteJid;

    @Indexed
    private String messageId;

    private Boolean fromMe;
    private String senderJid;
    private String senderName;
    private String pushName;

    // Message content
    private String messageType; // text, image, video, audio, document, sticker, location, contact, reaction, poll, etc.
    private String content; // text content or caption
    private String rawContent; // original raw content

    // Media
    private String mediaUrl;
    private String mediaMimetype;
    private Long mediaSize;
    private String mediaFileName;
    private byte[] mediaThumbnail;
    private Integer mediaWidth;
    private Integer mediaHeight;
    private Integer mediaDuration; // seconds for audio/video
    private Boolean isAnimated; // for stickers
    private Boolean isPtt; // push-to-talk audio
    private Boolean isViewOnce;

    // Quoted message
    private QuotedMessage quotedMessage;

    // Location
    private Double latitude;
    private Double longitude;
    private String locationName;
    private String locationAddress;

    // Contact
    private String contactVcard;
    private String contactName;

    // Reactions
    private List<ReactionInfo> reactions;

    // Edit
    private Boolean isEdited;
    private List<EditHistory> editHistory;

    // Status
    private Integer status; // 0=pending, 1=sent, 2=delivered, 3=read
    private Boolean isDeleted;
    private Boolean isStarred;

    // Group
    private String participant; // sender in group

    // Timestamp
    private Instant messageTimestamp;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Data
    public static class QuotedMessage {
        private String messageId;
        private String remoteJid;
        private String senderJid;
        private String content;
        private String messageType;
        private String mediaUrl;
        private byte[] thumbnail;
    }

    @Data
    public static class ReactionInfo {
        private String senderJid;
        private String emoji;
        private Instant timestamp;
    }

    @Data
    public static class EditHistory {
        private String content;
        private Instant editedAt;
    }
}
