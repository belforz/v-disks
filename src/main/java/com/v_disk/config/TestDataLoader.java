// package com.v_disk.config;

// import java.util.HashSet;
// import java.util.Set;

// import org.springframework.boot.CommandLineRunner;
// import org.springframework.context.annotation.Bean;
// import org.springframework.context.annotation.Configuration;

// import com.v_disk.model.User;
// import com.v_disk.repository.UserRepository;

// import org.springframework.security.crypto.password.PasswordEncoder;

// @Configuration
// public class TestDataLoader {

//     @Bean
//     public CommandLineRunner createTestUser(UserRepository userRepository, PasswordEncoder passwordEncoder) {
//         return args -> {
//             String email = "test@example.com";
//             if (!userRepository.existsByEmail(email)) {
//                 User u = new User();
//                 u.setEmail(email);
//                 u.setName("Test User");
//                 u.setPassword(passwordEncoder.encode("password123"));
//                 Set<String> roles = new HashSet<>();
//                 roles.add("USER");
//                 u.setRoles(roles);
//                 u.setEmailVerified(Boolean.TRUE);
//                 userRepository.save(u);
//                 System.out.println("[TestDataLoader] Created test user: " + email + " / password123");
//             } else {
//                 System.out.println("[TestDataLoader] Test user already exists: " + email);
//             }
//         };
//     }
// }
