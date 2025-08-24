package com.v_disk.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.bind.annotation.*;

import com.v_disk.utils.ResponseJSON;

@RestController
@RequestMapping("/api/mail")
public class EmailSender {
    @Autowired(required = false)
    private JavaMailSender mailSender;

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
        msg.setSubject("Bem-vindo ao v-disk!");
        msg.setText("Seu cadastro foi realizado com sucesso.");
        msg.setFrom("macedobeiramar@gmail.com");
        mailSender.send(msg);
        return ResponseEntity.ok(new ResponseJSON<>("success", "OK"));
    }

    @PostMapping("/send")
    public ResponseEntity<ResponseJSON<String>> sendEmail(@RequestParam String to, @RequestParam String subject, @RequestParam String body) {
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

}