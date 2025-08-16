package com.v_disk.dto.user;

import java.util.Set;

public record UserResponseDTO(
        String id,
        String name,
        String email,
        Set<String> roles,
        Boolean emailVerified) {

}
