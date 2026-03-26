package com.chatin.model;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document(collection = "reactions")
@CompoundIndex(name = "account_message_sender_idx", def = "{'accountId': 1, 'messageId': 1, 'senderJid': 1}", unique = true)
public class Reaction {

    @Id
    private String id;

    @Indexed
    private String accountId;

    @Indexed
    private String messageId;

    private String remoteJid;
    private String senderJid;
    private String senderName;
    private String emoji;
    private Boolean fromMe;

    // Reference to the message being reacted to
    private String targetMessageId;
    private String targetRemoteJid;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
