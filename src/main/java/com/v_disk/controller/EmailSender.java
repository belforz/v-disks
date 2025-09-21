package com.v_disk.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.v_disk.model.EmailVerificationToken;
import com.v_disk.model.User;
import com.v_disk.repository.EmailVerificationTokenRepository;
import com.v_disk.repository.UserRepository;
import com.v_disk.utils.ResponseJSON;

@RestController
@RequestMapping("/api/mail")
public class EmailSender {
    @Autowired(required = false)
    private JavaMailSender mailSender;

    private final UserRepository repo;
    private final EmailVerificationTokenRepository tokenRepo;
    private final String frontendUrl;
    private final long tokenTtlSeconds;
    private final String mailFrom;

    public EmailSender(UserRepository repo,
            EmailVerificationTokenRepository tokenRepo,
            @Value("${app.front.base-url:http://localhost}") String frontendUrl,
            @Value("${app.password_reset.ttl_seconds:3600}") long tokenTtlSeconds,
            @Value("${spring.mail.username:no-reply@v-disk.local}") String mailFrom) {
        this.repo = repo;
        this.tokenRepo = tokenRepo;
        this.frontendUrl = frontendUrl;
        this.tokenTtlSeconds = tokenTtlSeconds;
        this.mailFrom = mailFrom;
    }

    @GetMapping("/public")
    public ResponseEntity<ResponseJSON<String>> publicTest() {
        return ResponseEntity.ok(new ResponseJSON<>("success", "ok"));
    }

    @PostMapping("/test")
    public ResponseEntity<ResponseJSON<String>> send(@RequestParam String to) {
        if (mailSender == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ResponseJSON<>("error", "Mail service not configured"));
        }
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(to);
        msg.setSubject("Welcome to V-disk!");
        msg.setText("Your request has been validated.");
        msg.setFrom("macedobeiramar@gmail.com");
        mailSender.send(msg);
        return ResponseEntity.ok(new ResponseJSON<>("success", "OK"));
    }

    @PostMapping("/send")
    public ResponseEntity<ResponseJSON<String>> sendEmail(@RequestParam String to, @RequestParam String subject,
            @RequestParam String body) {
        if (mailSender == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ResponseJSON<>("error", "Mail service not configured"));
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
            return ResponseEntity.ok(new ResponseJSON<>("success", null));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseJSON<>("error", "Error sending email: " + e.getMessage()));
        }

    }

    @PostMapping("/change-password")
    public ResponseEntity<ResponseJSON<String>> sendEmailToClient(@RequestParam String to) {
        if (mailSender == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ResponseJSON<>("error", "Mail service not configured"));
        }
        User user = repo.findByEmail(to).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ResponseJSON<>("error", "User not found"));
        }

        try {
            tokenRepo.deleteByUserId(user.getId());
        } catch (Exception ignored) {
        }

        String token = UUID.randomUUID().toString();
        EmailVerificationToken t = new EmailVerificationToken();
        t.setUserId(user.getId());
        t.setToken(token);
        t.setExpiresAt(Instant.now().plusSeconds(tokenTtlSeconds));
        tokenRepo.save(t);

        String encoded = URLEncoder.encode(token, StandardCharsets.UTF_8);
        String link = frontendUrl + "?token=" + encoded;

        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(user.getEmail());
        msg.setSubject("Password change");
        msg.setText("Click on the link below to change your actual password:\n\n" + link
                + "\n\nIf you havent requested for, please ignore this email .");
        msg.setFrom(mailFrom);
        mailSender.send(msg);
        return ResponseEntity.ok(new ResponseJSON<>("success", "Changing email has been sent"));
    }

}