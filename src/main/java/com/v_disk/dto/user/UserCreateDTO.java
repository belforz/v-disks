package com.v_disk.dto.user;

import java.util.Set;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserCreateDTO(
    @NotBlank String name,
    @Email @NotBlank String email,
    @Size (min = 6, max = 24) String password,
    Set<String> roles
){}

