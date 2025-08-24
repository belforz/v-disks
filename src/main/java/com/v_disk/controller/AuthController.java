package com.v_disk.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.v_disk.service.EmailVerificationService;
import com.v_disk.utils.ResponseJSON;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final EmailVerificationService emailVerificationService;

    public AuthController(EmailVerificationService emailVerificationService) {
        this.emailVerificationService = emailVerificationService;
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
}
