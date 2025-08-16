package com.v_disk.demo;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mockito;
import static org.mockito.Mockito.when;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.v_disk.controller.UserController;
import com.v_disk.controller.VinylController;
import com.v_disk.model.User;
import com.v_disk.model.Vinyl;
import com.v_disk.repository.UserRepository;
import com.v_disk.repository.VinylRepository;

public class ApiControllersStandaloneTest {

    private MockMvc mvc;
    private ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private UserRepository userRepo;
    private VinylRepository vinylRepo;

    @BeforeEach
    void setup() {
        userRepo = Mockito.mock(UserRepository.class);
        vinylRepo = Mockito.mock(VinylRepository.class);

        UserController userController = new UserController(userRepo);
        VinylController vinylController = new VinylController(vinylRepo);

    this.mvc = MockMvcBuilders.standaloneSetup(userController, vinylController)
        .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
        .build();
    }

    @Test
    void users_listShouldReturnEmptyArrayWhenNoUsers() throws Exception {
        when(userRepo.findAll()).thenReturn(List.of());

        mvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void users_createShouldReturnCreated() throws Exception {
        Map<String, Object> payload = Map.of(
                "name", "Test User",
                "email", "t@example.com",
                "password", "secret12",
                "roles", List.of()
        );

        User saved = new User();
        saved.setId("u-1");
        saved.setName("Test User");
        saved.setEmail("t@example.com");
        saved.setRoles(new HashSet<>());

        when(userRepo.existsByEmail("t@example.com")).thenReturn(false);
        when(userRepo.save(any(User.class))).thenReturn(saved);

    var mvcResult = mvc.perform(post("/api/users")
        .contentType(MediaType.APPLICATION_JSON)
        .content(mapper.writeValueAsString(payload)))
        .andExpect(status().isCreated())
        .andReturn();

    String body = mvcResult.getResponse().getContentAsString();
    System.out.println("RESPONSE: " + body);
    org.assertj.core.api.Assertions.assertThat(body).contains("t@example.com");
    }

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
    void vinyl_createShouldReturnCreatedAndSavedVinyl() throws Exception {
        Map<String, Object> payload = Map.of(
                "title", "Test Album",
                "artist", "Test Artist",
                "price", 19.99,
                "stock", 5,
                "coverPath", "/covers/test.jpg",
                "gallery", List.of("/g1.jpg", "/g2.jpg")
        );

        Vinyl saved = new Vinyl();
        saved.setId("generated-id");
        saved.setTitle("Test Album");
        saved.setArtist("Test Artist");
        saved.setPrice(new BigDecimal("19.99"));
        saved.setStock(5);
        saved.setCoverPath("/covers/test.jpg");

        when(vinylRepo.save(any(Vinyl.class))).thenReturn(saved);

        mvc.perform(post("/api/vinyls")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("generated-id"))
                .andExpect(jsonPath("$.title").value("Test Album"));
    }
}
