package com.chatin.repository;

import com.chatin.model.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends MongoRepository<Message, String> {
    List<Message> findByAccountIdAndRemoteJidOrderByMessageTimestampDesc(String accountId, String remoteJid, Pageable pageable);
    List<Message> findByAccountIdAndRemoteJidAndMessageTimestampBeforeOrderByMessageTimestampDesc(
            String accountId, String remoteJid, Instant before, Pageable pageable);
    Optional<Message> findByAccountIdAndMessageId(String accountId, String messageId);
    List<Message> findByAccountId(String accountId);
    long countByAccountId(String accountId);
    long countByAccountIdAndRemoteJid(String accountId, String remoteJid);
    long countByAccountIdAndMessageTimestampAfter(String accountId, Instant after);
    long countByAccountIdAndFromMe(String accountId, Boolean fromMe);
    void deleteByAccountId(String accountId);
    List<Message> findByAccountIdAndRemoteJidAndFromMe(String accountId, String remoteJid, Boolean fromMe);
}
