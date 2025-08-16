package com.v_disk.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.v_disk.model.User;

public interface UserRepository  extends MongoRepository<User, String>{
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
