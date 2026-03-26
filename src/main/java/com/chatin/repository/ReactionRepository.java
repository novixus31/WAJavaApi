package com.chatin.repository;

import com.chatin.model.Reaction;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReactionRepository extends MongoRepository<Reaction, String> {
    List<Reaction> findByAccountIdAndMessageId(String accountId, String messageId);
    Optional<Reaction> findByAccountIdAndMessageIdAndSenderJid(String accountId, String messageId, String senderJid);
    void deleteByAccountId(String accountId);
}
