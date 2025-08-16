package com.v_disk.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.v_disk.controller.UserController;
import com.v_disk.controller.VinylController;
import com.v_disk.model.Vinyl;
import com.v_disk.repository.UserRepository;
import com.v_disk.repository.VinylRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Teste leve e moderno:
 * - JUnit 5 + Mockito Extension
 * - MockMvc standalone (não carrega contexto do Spring, sem @MockBean)
 * - Controllers reais, repositórios mockados
 */
@ExtendWith(MockitoExtension.class)
public class ApiControllersTest {

    private MockMvc mvc;
    private final ObjectMapper mapper = new ObjectMapper();

    // Mocks dos repositórios (sem @MockBean)
    @Mock
    private UserRepository userRepo;

    @Mock
    private VinylRepository vinylRepo;

    // Controllers reais injetados com os mocks
    @InjectMocks
    private UserController userController;

    @InjectMocks
    private VinylController vinylController;

    @BeforeEach
    void setup() {
        // Monta o MockMvc sem subir o contexto do Spring
        this.mvc = MockMvcBuilders
                .standaloneSetup(userController, vinylController)
                // .setControllerAdvice(new SeuAdviceGlobalOpcional())
                .build();
    }

    /* ===================== USERS ===================== */

    @Test
    void users_listShouldReturnEmptyArrayWhenNoUsers() throws Exception {
        when(userRepo.findAll()).thenReturn(List.of());

        mvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void users_getShouldReturn404WhenNotFound() throws Exception {
        when(userRepo.findById("nonexistent")).thenReturn(Optional.empty());

        mvc.perform(get("/api/users/nonexistent"))
                .andExpect(status().isNotFound());
    }

    @Test
    void users_createShouldReturn409WhenEmailExists() throws Exception {
        Map<String, Object> payload = Map.of(
                "name", "Test User",
                "email", "t@example.com",
                "password", "secret12",
                "roles", List.of()
        );

        when(userRepo.existsByEmail("t@example.com")).thenReturn(true);

        mvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(payload)))
                .andExpect(status().isConflict());
    }

    /* ===================== VINYLS ===================== */

    @Test
    void vinyls_listShouldReturnVinyls() throws Exception {
        Vinyl v = new Vinyl();
        v.setId("1");
        v.setTitle("Dark Side of the Moon");
        v.setArtist("Pink Floyd");
        v.setPrice(new BigDecimal("29.90"));
        v.setStock(10);

        when(vinylRepo.findAll()).thenReturn(List.of(v));

        mvc.perform(get("/api/vinyls"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Dark Side of the Moon"))
                .andExpect(jsonPath("$[0].artist").value("Pink Floyd"));
    }

    @Test
    void vinyls_createShouldReturnCreatedAndSavedVinyl() throws Exception {
        var payload = new java.util.HashMap<String, Object>();
        payload.put("title", "Test Album");
        payload.put("artist", "Test Artist");
        payload.put("price", 19.99);
        payload.put("stock", 5);
        payload.put("coverPath", "/covers/test.jpg");
        payload.put("gallery", List.of("/g1.jpg", "/g2.jpg"));

        Vinyl saved = new Vinyl();
        saved.setId("generated-id");
        saved.setTitle("Test Album");
        saved.setArtist("Test Artist");
        saved.setPrice(new BigDecimal("19.99"));
        saved.setStock(5);
        saved.setCoverPath("/covers/test.jpg");

        when(vinylRepo.save(ArgumentMatchers.any(Vinyl.class))).thenReturn(saved);

        mvc.perform(post("/api/vinyls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("generated-id"))
                .andExpect(jsonPath("$.title").value("Test Album"));
    }
}
