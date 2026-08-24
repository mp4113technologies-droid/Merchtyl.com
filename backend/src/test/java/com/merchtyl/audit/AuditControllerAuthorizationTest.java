package com.merchtyl.audit;

import com.merchtyl.common.PageResponse;
import com.merchtyl.security.AuthorizationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = AuditControllerAuthorizationTest.TestApplication.class)
@AutoConfigureMockMvc
class AuditControllerAuthorizationTest {
    private static final UUID AUDIT_ID = UUID.fromString("00000000-0000-0000-0000-000000000801");
    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000802");
    private static final UUID STORE_ID = UUID.fromString("00000000-0000-0000-0000-000000000803");

    @Autowired
    MockMvc mockMvc;

    @MockBean
    AuditService auditService;

    @Test
    void auditListRequiresAuditViewPermission() throws Exception {
        mockMvc.perform(get("/api/v1/audit")
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("SALE_VIEW"))))
                .andExpect(status().isForbidden());

        verify(auditService, never()).search(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void auditListSupportsPaginationAndFilters() throws Exception {
        when(auditService.search(org.mockito.ArgumentMatchers.any())).thenReturn(new PageResponse<>(
                List.of(new AuditRecordResponse(
                        AUDIT_ID,
                        ACTOR_ID,
                        AuditAction.LOGIN_SUCCESS.name(),
                        "USER",
                        ACTOR_ID,
                        STORE_ID,
                        null,
                        null,
                        "{\"status\":\"success\"}",
                        null,
                        "corr-123",
                        Instant.parse("2026-07-21T12:00:00Z"))),
                2,
                10,
                21,
                3,
                false,
                true));

        mockMvc.perform(get("/api/v1/audit")
                        .with(user("owner").authorities(new SimpleGrantedAuthority("AUDIT_VIEW")))
                        .param("action", "login_success")
                        .param("entityType", "user")
                        .param("entityId", ACTOR_ID.toString())
                        .param("actorUserId", ACTOR_ID.toString())
                        .param("storeId", STORE_ID.toString())
                        .param("createdFrom", "2026-07-21T00:00:00Z")
                        .param("createdTo", "2026-07-22T00:00:00Z")
                        .param("page", "2")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(AUDIT_ID.toString()))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(21));

        ArgumentCaptor<AuditSearchRequest> request = ArgumentCaptor.forClass(AuditSearchRequest.class);
        verify(auditService).search(request.capture());
        assertThat(request.getValue().action()).isEqualTo("login_success");
        assertThat(request.getValue().entityType()).isEqualTo("user");
        assertThat(request.getValue().entityId()).isEqualTo(ACTOR_ID);
        assertThat(request.getValue().actorUserId()).isEqualTo(ACTOR_ID);
        assertThat(request.getValue().storeId()).isEqualTo(STORE_ID);
        assertThat(request.getValue().page()).isEqualTo(2);
        assertThat(request.getValue().size()).isEqualTo(10);
    }

    @Test
    void auditGetRequiresAuditViewPermission() throws Exception {
        mockMvc.perform(get("/api/v1/audit/{id}", AUDIT_ID)
                        .with(user("manager").authorities(new SimpleGrantedAuthority("REPORT_VIEW"))))
                .andExpect(status().isForbidden());

        verify(auditService, never()).get(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void auditViewerCanGetAuditRecord() throws Exception {
        when(auditService.get(AUDIT_ID)).thenReturn(new AuditRecordResponse(
                AUDIT_ID,
                ACTOR_ID,
                AuditAction.LOGOUT.name(),
                "USER",
                ACTOR_ID,
                null,
                null,
                null,
                "{\"status\":\"logged_out\"}",
                null,
                "corr-456",
                Instant.parse("2026-07-21T13:00:00Z")));

        mockMvc.perform(get("/api/v1/audit/{id}", AUDIT_ID)
                        .with(user("owner").authorities(new SimpleGrantedAuthority("AUDIT_VIEW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(AUDIT_ID.toString()))
                .andExpect(jsonPath("$.action").value(AuditAction.LOGOUT.name()));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            JpaRepositoriesAutoConfiguration.class
    })
    @EnableMethodSecurity
    @Import({AuditController.class, AuthorizationService.class, TestSecurityConfig.class})
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
