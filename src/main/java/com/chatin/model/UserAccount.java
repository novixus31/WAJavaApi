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
@Document(collection = "useraccounts")
@CompoundIndex(name = "user_account_idx", def = "{'userId': 1, 'accountId': 1}", unique = true)
public class UserAccount {

    @Id
    private String id;

    @Indexed
    private String userId;

    @Indexed
    private String accountId;

    private Integer order;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
