package com.chatin.repository;

import com.chatin.model.Account;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccountRepository extends MongoRepository<Account, String> {
    List<Account> findByCompanyId(String companyId);
    List<Account> findByCompanyIdOrderByCompanyOrderAsc(String companyId);
    List<Account> findByStatus(String status);
    List<Account> findByCompanyIdAndStatus(String companyId, String status);
    List<Account> findByIdIn(List<String> ids);
    long countByCompanyId(String companyId);
    long countByCompanyIdAndStatus(String companyId, String status);
    long countByStatus(String status);
}
