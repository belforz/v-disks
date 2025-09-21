package com.v_disk.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import com.v_disk.model.EmailVerificationToken;
import com.v_disk.model.User;
import com.v_disk.repository.EmailVerificationTokenRepository;
import com.v_disk.repository.UserRepository;

@Service
public class EmailVerificationService {

    private final EmailVerificationTokenRepository tokenRepo;
    private final UserRepository userRepo;
    private final JavaMailSender mailSender;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(EmailVerificationService.class);

    
    @Value("${app.email.from:${spring.mail.username:no-reply@local}}")
    private String fromAddress;

    @Value("${app.front.base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    @Value("${app.front.verify-path:/verify-email}")
    private String verifyPath;

    public EmailVerificationService(EmailVerificationTokenRepository tokenRepo, UserRepository userRepo, JavaMailSender mailSender) {
        this.tokenRepo = tokenRepo;
        this.userRepo = userRepo;
        this.mailSender = mailSender;
    }

    public EmailVerificationToken createTokenForUser(User user) {
        tokenRepo.deleteByUserId(user.getId());

        EmailVerificationToken t = new EmailVerificationToken();
        t.setUserId(user.getId());
        t.setToken(UUID.randomUUID().toString());
        t.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));
        return tokenRepo.save(t);
    }

    public Optional<EmailVerificationToken> findByToken(String token) {
        return tokenRepo.findByToken(token);
    }

    public void sendVerificationEmail(User user, EmailVerificationToken token) {
        if (mailSender == null) return;
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(user.getEmail());
        msg.setFrom(fromAddress);
        msg.setSubject("Confirme seu e-mail");

        // Normalize and build the verification link safely.
        // Cases to handle:
        //  - verifyPath is an absolute URL (starts with http/https) -> use it directly
        //  - verifyPath is an absolute path (/verify-email) -> combine with frontendBaseUrl without duplicating slashes
        //  - verifyPath accidentally contains the frontendBaseUrl already -> avoid double prefixing
        String link;
        try {
            String trimmedVerify = verifyPath == null ? "" : verifyPath.trim();
            String trimmedBase = frontendBaseUrl == null ? "" : frontendBaseUrl.trim();

            // If verifyPath looks like a full URL, use it directly but ensure token param is appended
            if (trimmedVerify.matches("(?i)^https?://.*")) {
                link = UriComponentsBuilder.fromUriString(trimmedVerify)
                    .queryParam("token", token.getToken())
                    .toUriString();
            } else {
                // If verifyPath already contains the base URL, strip it out to avoid duplication
                if (!trimmedBase.isEmpty() && trimmedVerify.startsWith(trimmedBase)) {
                    trimmedVerify = trimmedVerify.substring(trimmedBase.length());
                }
                // Ensure single slash between base and path
                String sep = "";
                if (!trimmedBase.endsWith("/") && !trimmedVerify.startsWith("/")) sep = "/";
                if (trimmedBase.endsWith("/") && trimmedVerify.startsWith("/")) trimmedVerify = trimmedVerify.substring(1);

                link = UriComponentsBuilder.fromUriString(trimmedBase + sep + trimmedVerify)
                    .queryParam("token", token.getToken())
                    .toUriString();
            }
        } catch (Exception e) {
            // Fall back to a simple concatenation in unlikely error cases
            link = frontendBaseUrl + (verifyPath.startsWith("/") ? "" : "/") + verifyPath + "?token=" + token.getToken();
        }

        String body = "Hi " + (user.getName() == null ? "" : user.getName()) + ",\n\n" +
            "Please click the link below to confirm your email address:\n" + link + "\n\n" +
            "If you did not request this, please ignore this email.";
        msg.setText(body);
        mailSender.send(msg);
    }

    
    public VerificationStatus verifyToken(String tokenStr) {
        logger.info("Verifying token lookup for token='{}' (len={})", tokenStr, tokenStr == null ? 0 : tokenStr.length());

        // Try direct lookup first
        Optional<EmailVerificationToken> opt = tokenRepo.findByToken(tokenStr);
        logger.info("Direct token lookup present={}", opt.isPresent());

        // If not found, try decoding + sanitizing common email pitfalls (trailing dot, whitespace)
        if (opt.isEmpty() && tokenStr != null) {
            try {
                String decoded = java.net.URLDecoder.decode(tokenStr, java.nio.charset.StandardCharsets.UTF_8.name());
                decoded = decoded.trim();
                if (decoded.endsWith(".")) {
                    decoded = decoded.substring(0, decoded.length() - 1);
                }
                if (!decoded.equals(tokenStr)) {
                    logger.info("Trying decoded/sanitized token='{}' (len={})", decoded, decoded.length());
                    opt = tokenRepo.findByToken(decoded);
                    logger.info("Decoded token lookup present={}", opt.isPresent());
                }
            } catch (Exception e) {
                logger.warn("Failed to decode token string: {}", e.getMessage());
            }
        }

        if (opt.isEmpty()) return VerificationStatus.NOT_FOUND;
        EmailVerificationToken token = opt.get();
        if (token.getExpiresAt() == null || token.getExpiresAt().isBefore(Instant.now())) {
            tokenRepo.delete(token);
            return VerificationStatus.EXPIRED;
        }
        Optional<User> ou = userRepo.findById(token.getUserId());
        if (ou.isEmpty()) return VerificationStatus.NOT_FOUND;
        User u = ou.get();
        if (u.isEmailVerified()) {
            tokenRepo.deleteByUserId(u.getId());
            return VerificationStatus.SUCCESS;
        }
        u.setEmailVerified(true);
        userRepo.save(u);
        tokenRepo.deleteByUserId(u.getId());
        return VerificationStatus.SUCCESS;
    }

    public Optional<EmailVerificationToken> getTokenForUserId(String userId) {
        return tokenRepo.findByUserId(userId);
    }
}
