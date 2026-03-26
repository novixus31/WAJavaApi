package com.chatin.repository;

import com.chatin.model.Chat;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRepository extends MongoRepository<Chat, String> {
    List<Chat> findByAccountId(String accountId, Sort sort);
    List<Chat> findByAccountId(String accountId);
    Optional<Chat> findByAccountIdAndRemoteJid(String accountId, String remoteJid);
    List<Chat> findByAccountIdIn(List<String> accountIds);
    long countByAccountId(String accountId);
    void deleteByAccountId(String accountId);
}
