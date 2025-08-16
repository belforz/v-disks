package com.v_disk.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import com.v_disk.controller.UserController;
import com.v_disk.controller.VinylController;
import com.v_disk.repository.UserRepository;
import com.v_disk.repository.VinylRepository;

@WebMvcTest(controllers = { UserController.class, VinylController.class },
           excludeAutoConfiguration = {org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class})
@AutoConfigureMockMvc(addFilters = false)
public class MappingsDumpTest {

    @Autowired
    private RequestMappingHandlerMapping mapping;

    // mock repositories so controllers podem ser instanciados se precisarem
    @MockBean private UserRepository userRepo;
    @MockBean private VinylRepository vinylRepo;
   
    @Test
    void printAllMappings() {
        for (var entry : mapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            HandlerMethod method = entry.getValue();
            System.out.println(info + " -> " + method.getMethod().getDeclaringClass().getSimpleName()
                    + "#" + method.getMethod().getName());
        }
    }
}