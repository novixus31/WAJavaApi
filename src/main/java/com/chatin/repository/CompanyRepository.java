package com.chatin.repository;

import com.chatin.model.Company;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CompanyRepository extends MongoRepository<Company, String> {
    Optional<Company> findByName(String name);
    long countByIsActive(Boolean isActive);
}
