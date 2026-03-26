package com.chatin.repository;

import com.chatin.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByEmail(String email);
    List<User> findByCompanyId(String companyId);
    List<User> findByRole(String role);
    long countByCompanyId(String companyId);
    long countByRole(String role);
    boolean existsByEmail(String email);
}
