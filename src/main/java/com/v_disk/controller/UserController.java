package com.v_disk.controller;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserRepository repo;

    public UserController(UserRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public List<UserResponseDTO> list() {
        return repo.findAll()
                   .stream()
                   .map(this::toDTO)
                   .collect(Collectors.toList());
    }

    private UserResponseDTO toDTO(User user) {
        Set<String> roles = user.getRoles() == null ? Set.of() : user.getRoles();

        return new UserResponseDTO(
            user.getId(),
            user.getName(),
            user.getEmail(),
            roles,
            user.isEmailVerified()
        );
    }
    @GetMapping("/{id}")
    public UserResponseDTO get(@PathVariable String id){
        return repo.findById(id).map(this::toDTO).orElseThrow(()-> new ResponseStatusException(HttpStatus.NOT_FOUND));

    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponseDTO create(@RequestBody @Valid UserCreateDTO dto) {
        if (repo.existsByEmail(dto.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
        }

        User user = new User();
        user.setName(dto.name());
        user.setEmail(dto.email());
        user.setPassword(dto.password());

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

    return toDTO(saved);
    }
    @PatchMapping("{id}")
    public UserResponseDTO update(@PathVariable String id, @RequestBody @Valid UserUpdateDTO dto) {
        User user = repo.findById(id).orElseThrow(()-> new ResponseStatusException(HttpStatus.NOT_FOUND,"User not found"));
        if(dto.name() != null){
            user.setName(dto.name());
        }
        if(dto.email() != null){
            user.setEmail(dto.email());
        }
        if(dto.password() != null){
            user.setPassword(dto.password());
        }
        if(dto.roles() != null){
            user.setRoles(new HashSet<>(dto.roles()));
        }
        if(dto.emailVerified() != null){
            user.setEmailVerified(dto.emailVerified());
        }
        repo.save(user);
        return toDTO(user);
    }

    @DeleteMapping("/id")
    @ResponseStatus(HttpStatus.OK)
    public void delete(@PathVariable String id){
        if(!repo.existsById(id)){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"User not found");
        }
        repo.deleteById(id);
    }

}
