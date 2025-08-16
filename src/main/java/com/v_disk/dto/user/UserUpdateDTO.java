package com.v_disk.dto.user;

import java.util.Set;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UserUpdateDTO(
    String name,
    @Email String email,
    Set<String> roles,
    @Size (min = 6, max = 24) String password,
    Boolean emailVerified
){}


