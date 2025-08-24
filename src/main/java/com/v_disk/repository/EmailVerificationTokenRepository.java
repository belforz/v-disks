package com.v_disk.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.v_disk.model.EmailVerificationToken;

public interface EmailVerificationTokenRepository extends MongoRepository<EmailVerificationToken, String> {
    Optional<EmailVerificationToken> findByToken(String token);
    Optional<EmailVerificationToken> findByUserId(String userId);
    void deleteByUserId(String userId);
}
