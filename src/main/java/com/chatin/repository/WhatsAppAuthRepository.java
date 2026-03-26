package com.chatin.repository;

import com.chatin.model.WhatsAppAuth;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WhatsAppAuthRepository extends MongoRepository<WhatsAppAuth, String> {
    List<WhatsAppAuth> findByAccountId(String accountId);
    Optional<WhatsAppAuth> findByAccountIdAndType(String accountId, String type);
    Optional<WhatsAppAuth> findByAccountIdAndTypeAndKeyId(String accountId, String type, String keyId);
    void deleteByAccountId(String accountId);
}
