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
@Document(collection = "whatsappauths")
@CompoundIndex(name = "accountId_type_idx", def = "{'accountId': 1, 'type': 1}")
public class WhatsAppAuth {

    @Id
    private String id;

    @Indexed
    private String accountId;

    private String type; // "creds" or "keys"
    private String keyId; // for keys type, the specific key identifier
    private Object data; // the actual auth data (credentials or keys)

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
