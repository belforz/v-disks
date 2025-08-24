package com.v_disk.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.v_disk.model.EmailVerificationToken;
import com.v_disk.model.User;
import com.v_disk.repository.EmailVerificationTokenRepository;
import com.v_disk.repository.UserRepository;

@Service
public class EmailVerificationService {

    private final EmailVerificationTokenRepository tokenRepo;
    private final UserRepository userRepo;
    private final JavaMailSender mailSender;

    
    @Value("${app.email.from:${spring.mail.username:no-reply@local}}")
    private String fromAddress;

   
    @Value("${app.email.verify.url:http://localhost:8080/auth/verify-email}")
    private String verifyUrl;

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
        String link = verifyUrl + "?token=" + token.getToken();
        String body = "Olá " + (user.getName() == null ? "" : user.getName()) + ",\n\n" +
                "Clique no link abaixo para confirmar seu e-mail:\n" + link + "\n\n" +
                "Se não foi você, ignore esta mensagem.";
        msg.setText(body);
        mailSender.send(msg);
    }

    
    public VerificationStatus verifyToken(String tokenStr) {
        Optional<EmailVerificationToken> opt = tokenRepo.findByToken(tokenStr);
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
