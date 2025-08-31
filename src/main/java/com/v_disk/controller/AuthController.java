package com.v_disk.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.v_disk.model.User;
import com.v_disk.repository.UserRepository;
import com.v_disk.service.EmailVerificationService;
import com.v_disk.service.JwtService;
import com.v_disk.utils.ResponseJSON;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final EmailVerificationService emailVerificationService;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.v_disk.repository.EmailVerificationTokenRepository tokenRepository;

    public AuthController(
            EmailVerificationService emailVerificationService, 
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            com.v_disk.repository.EmailVerificationTokenRepository tokenRepository) {
        this.emailVerificationService = emailVerificationService;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenRepository = tokenRepository;
    }
    
    public static class LoginRequest {
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        private String email;
        
        @NotBlank(message = "Password is required")
        private String password;
        
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
    
    public static class TokenResponse {
        private final String token;
        private final String type = "Bearer";
        
        public TokenResponse(String token) {
            this.token = token;
        }
        
        public String getToken() { return token; }
        public String getType() { return type; }
    }

    @PostMapping({"/", "/login"})
    public ResponseEntity<ResponseJSON<TokenResponse>> login(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            // Authenticate the user
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword())
            );
            
            SecurityContextHolder.getContext().setAuthentication(authentication);
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            
            // Find user to get additional info
            User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
            
            // Generate JWT token with user details
            Map<String, Object> claims = new HashMap<>();
            claims.put("userId", user.getId());
            claims.put("roles", user.getRoles());
            claims.put("name", user.getName());
            
            String jwt = jwtService.generateToken(user.getEmail(), claims);
            
            return ResponseEntity.ok(new ResponseJSON<>("success", new TokenResponse(jwt)));
        } catch (AuthenticationException e) {
            return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new ResponseJSON<TokenResponse>("error", null));
        }
    }

    @PostMapping("/verify-email")
    public ResponseEntity<ResponseJSON<String>> verifyEmail(@RequestParam(required = false) String token, @RequestBody(required = false) Map<String, String> body) {
        String t = token;
        if ((t == null || t.isBlank()) && body != null) {
            t = body.get("token");
        }
        if (t == null || t.isBlank()) {
            return ResponseEntity.badRequest().body(new ResponseJSON<>("error", "token_required"));
        }
        // verify token
        var status = emailVerificationService.verifyToken(t);
        switch (status) {
            case SUCCESS:
                return ResponseEntity.ok(new ResponseJSON<>("success", "verified"));
            case EXPIRED:
                return ResponseEntity.status(HttpStatus.GONE).body(new ResponseJSON<>("error", "expired"));
            case NOT_FOUND:
            default:
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ResponseJSON<>("error", "invalid_or_not_found"));
        }
    }

    public static class ChangePasswordRequest {
        private String token;
        private String newPassword;

        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
        public String getNewPassword() { return newPassword; }
        public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    }

    @PostMapping("/change-password")
    // Route for changing password
    public ResponseEntity<ResponseJSON<String>> changePassword(@RequestParam(required = false) String token, @RequestBody(required = false) ChangePasswordRequest body) {
        String t = token;
        if ((t == null || t.isBlank()) && body != null) t = body.getToken();
        if (t == null || t.isBlank()) {
            return ResponseEntity.badRequest().body(new ResponseJSON<>("error", "token_required"));
        }
        String newPassword = body != null ? body.getNewPassword() : null;
        if (newPassword == null || newPassword.isBlank()) {
            return ResponseEntity.badRequest().body(new ResponseJSON<>("error", "password_required"));
        }

        // Search token
        var opt = emailVerificationService.findByToken(t);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ResponseJSON<>("error", "invalid_or_not_found"));
        }
        var tokenObj = opt.get();
        if (tokenObj.getExpiresAt() == null || tokenObj.getExpiresAt().isBefore(java.time.Instant.now())) {
            try { tokenRepository.delete(tokenObj); } catch (Exception ignored) {}
            return ResponseEntity.status(HttpStatus.GONE).body(new ResponseJSON<>("error", "expired"));
        }
        var ou = userRepository.findById(tokenObj.getUserId());
        if (ou.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ResponseJSON<>("error", "user_not_found"));
        }
        // If finded, sucess
        var u = ou.get();
        u.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(u);
        try { tokenRepository.deleteByUserId(u.getId()); } catch (Exception ignored) {}
        return ResponseEntity.ok(new ResponseJSON<>("success", "password_changed"));
    }
}
