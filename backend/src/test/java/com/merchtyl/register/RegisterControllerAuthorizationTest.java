package com.merchtyl.register;

import com.merchtyl.common.PageResponse;
import com.merchtyl.security.AuthorizationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = RegisterControllerAuthorizationTest.TestApplication.class)
@AutoConfigureMockMvc
class RegisterControllerAuthorizationTest {
    private static final UUID STORE_ID = UUID.fromString("00000000-0000-0000-0000-000000000901");
    private static final UUID REGISTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000902");
    private static final String REGISTER_JSON = """
            {
              "storeId": "00000000-0000-0000-0000-000000000901",
              "code": "FRONT-1",
              "name": "Front Register",
              "locationDescription": "Front counter",
              "active": true
            }
            """;

    @Autowired
    MockMvc mockMvc;

    @MockBean
    RegisterService registerService;

    @Test
    void registerViewerCannotCreateRegister() throws Exception {
        mockMvc.perform(post("/api/v1/registers")
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("REGISTER_VIEW")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_JSON))
                .andExpect(status().isForbidden());

        verify(registerService, never()).create(any(), any());
    }

    @Test
    void registerManagerCanCreateRegister() throws Exception {
        when(registerService.create(any(), any())).thenReturn(response(true, 0));

        mockMvc.perform(post("/api/v1/registers")
                        .with(user("manager").authorities(new SimpleGrantedAuthority("REGISTER_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(REGISTER_ID.toString()))
                .andExpect(jsonPath("$.storeId").value(STORE_ID.toString()))
                .andExpect(jsonPath("$.code").value("FRONT-1"));
    }

    @Test
    void registerListRequiresViewPermission() throws Exception {
        mockMvc.perform(get("/api/v1/registers")
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("SALE_VIEW"))))
                .andExpect(status().isForbidden());

        verify(registerService, never()).search(any());
    }

    @Test
    void registerViewerCanListRegisters() throws Exception {
        when(registerService.search(any())).thenReturn(new PageResponse<>(
                List.of(response(true, 0)),
                0,
                20,
                1,
                1,
                true,
                true));

        mockMvc.perform(get("/api/v1/registers")
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("REGISTER_VIEW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(REGISTER_ID.toString()));
    }

    @Test
    void statusPatchRequiresManagePermission() throws Exception {
        mockMvc.perform(patch("/api/v1/registers/{id}/status", REGISTER_ID)
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("REGISTER_VIEW")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "active": false,
                                  "version": 0
                                }
                                """))
                .andExpect(status().isForbidden());

        verify(registerService, never()).updateStatus(any(), any(), any());
    }

    private static RegisterResponse response(boolean active, long version) {
        return new RegisterResponse(
                REGISTER_ID,
                STORE_ID,
                "FRONT-1",
                "Front Register",
                "Front counter",
                active,
                Instant.parse("2026-07-21T12:00:00Z"),
                Instant.parse("2026-07-21T12:00:00Z"),
                version);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            JpaRepositoriesAutoConfiguration.class
    })
    @EnableMethodSecurity
    @Import({RegisterController.class, AuthorizationService.class, TestSecurityConfig.class})
    static class TestApplication {
    }

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .build();
        }
    }
}
