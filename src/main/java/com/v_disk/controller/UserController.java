package com.v_disk.controller;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.v_disk.dto.user.UserCreateDTO;
import com.v_disk.dto.user.UserResponseDTO;
import com.v_disk.dto.user.UserUpdateDTO;
import com.v_disk.model.User;
import com.v_disk.repository.UserRepository;
import com.v_disk.utils.ResponseJSON;
import com.v_disk.service.EmailVerificationService;
import com.v_disk.model.EmailVerificationToken;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserRepository repo;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;

    public UserController(UserRepository repo, PasswordEncoder passwordEncoder,
            EmailVerificationService emailVerificationService) {
        this.repo = repo;
        this.passwordEncoder = passwordEncoder;
        this.emailVerificationService = emailVerificationService;
    }

    @GetMapping
    public ResponseEntity<ResponseJSON<List<UserResponseDTO>>> list() {
        List<UserResponseDTO> users = repo.findAll()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(new ResponseJSON<>("Listed successfully", users));
    }

    private UserResponseDTO toDTO(User user) {
        Set<String> roles = user.getRoles() == null ? Set.of() : user.getRoles();

        return new UserResponseDTO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                roles,
                user.isEmailVerified());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseJSON<UserResponseDTO>> get(@PathVariable String id) {
        UserResponseDTO dto = repo.findById(id).map(this::toDTO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return ResponseEntity.ok(new ResponseJSON<>("Listed one successfully", dto));

    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ResponseJSON<UserResponseDTO>> create(@RequestBody @Valid UserCreateDTO dto) {
        if (repo.existsByEmail(dto.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
        }

        User user = new User();
        user.setName(dto.name());
        user.setEmail(dto.email());
        user.setPassword(passwordEncoder.encode((dto.password())));

        Set<String> chosenRoles;
        if (dto.roles() == null || dto.roles().isEmpty()) {
            chosenRoles = Set.of("USER");
        } else if (dto.roles().contains("ADMIN")) {
            chosenRoles = Set.of("ADMIN");
        } else {
            chosenRoles = Set.of("USER");
        }
        user.setRoles(new HashSet<>(chosenRoles));
        User saved = repo.save(user);

        try {
            EmailVerificationToken token = emailVerificationService.createTokenForUser(saved);
            emailVerificationService.sendVerificationEmail(saved, token);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to send verification email");
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(new ResponseJSON<>("Created Successfully", toDTO(saved)));
    }
    
    @PostMapping("/resend-verification/{userId}")
    public ResponseEntity<ResponseJSON<String>> resendVerification(@PathVariable String userId) {
        User user = repo.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (user.isEmailVerified())
            return ResponseEntity.badRequest().body(new ResponseJSON<>("error", "already_verified"));
        EmailVerificationToken token = emailVerificationService.createTokenForUser(user);
        emailVerificationService.sendVerificationEmail(user, token);
        return ResponseEntity.ok(new ResponseJSON<>("success", "sent"));
    }

    @GetMapping("/verify")
    public ResponseEntity<ResponseJSON<String>> verify(@RequestParam String token) {
        com.v_disk.service.VerificationStatus status = emailVerificationService.verifyToken(token);
        switch (status) {
            case SUCCESS:
                return ResponseEntity.ok(new ResponseJSON<>("success", "verified"));
            case EXPIRED:
                return ResponseEntity.status(HttpStatus.GONE).body(new ResponseJSON<>("error", "expired"));
            case NOT_FOUND:
            default:
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ResponseJSON<>("error", "invalid_or_not_found"));
        }
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ResponseJSON<UserResponseDTO>> update(@PathVariable String id,
            @RequestBody @Valid UserUpdateDTO dto) {
        User user = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (dto.name() != null) {
            user.setName(dto.name());
        }
        if (dto.email() != null) {
            user.setEmail(dto.email());
        }
        if (dto.password() != null) {
            user.setPassword(passwordEncoder.encode((dto.password())));
        }
        if (dto.roles() != null) {
            user.setRoles(new HashSet<>(dto.roles()));
        }
        if (dto.emailVerified() != null) {
            user.setEmailVerified(dto.emailVerified());
        }
        repo.save(user);
        return ResponseEntity.ok(new ResponseJSON<>("Edited Successfully", toDTO(user)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseJSON<String>> delete(@PathVariable String id) {
        if (!repo.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        repo.deleteById(id);
        return ResponseEntity.ok(new ResponseJSON<>("Deleted Successfully", id));
    }

}
