package com.chatin.repository;

import com.chatin.model.UserAccount;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserAccountRepository extends MongoRepository<UserAccount, String> {
    List<UserAccount> findByUserId(String userId);
    List<UserAccount> findByUserIdOrderByOrderAsc(String userId);
    List<UserAccount> findByAccountId(String accountId);
    Optional<UserAccount> findByUserIdAndAccountId(String userId, String accountId);
    void deleteByAccountId(String accountId);
    void deleteByUserId(String userId);
    long countByAccountId(String accountId);
}
