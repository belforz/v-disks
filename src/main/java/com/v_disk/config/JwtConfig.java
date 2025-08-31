package com.v_disk.config;

import java.security.Key;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class JwtConfig {
    @Bean
    public Key jwtKey(@Value("${jwt.secret.base64}") String secret) {
        byte[] raw = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(raw);
    }
    
}
