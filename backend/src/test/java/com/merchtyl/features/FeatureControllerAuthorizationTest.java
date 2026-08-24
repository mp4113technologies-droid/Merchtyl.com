package com.merchtyl.features;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = FeatureControllerAuthorizationTest.TestApplication.class)
@AutoConfigureMockMvc
class FeatureControllerAuthorizationTest {
    private static final UUID FEATURE_ID = UUID.fromString("00000000-0000-0000-0000-000000000f04");

    @Autowired
    MockMvc mockMvc;

    @MockBean
    FeatureService featureService;

    @Test
    void definitionsRequireFeatureViewPermission() throws Exception {
        mockMvc.perform(get("/api/v1/features/definitions")
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("SALE_VIEW"))))
                .andExpect(status().isForbidden());

        verify(featureService, never()).listDefinitions();
    }

    @Test
    void viewerCanListResolvedFeatures() throws Exception {
        when(featureService.resolve(any(), any())).thenReturn(List.of(resolution(true, FeatureResolutionSource.DEFAULT)));

        mockMvc.perform(get("/api/v1/features/resolution")
                        .with(user("manager").authorities(new SimpleGrantedAuthority("FEATURE_VIEW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].definition.code").value("AGE_VERIFICATION"))
                .andExpect(jsonPath("$[0].enabled").value(true));
    }

    @Test
    void updateRequiresFeatureManagePermission() throws Exception {
        mockMvc.perform(put("/api/v1/features/AGE_VERIFICATION/deployment")
                        .with(user("manager").authorities(new SimpleGrantedAuthority("FEATURE_VIEW")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled": false
                                }
                                """))
                .andExpect(status().isForbidden());

        verify(featureService, never()).updateDeployment(any(), any(), any());
    }

    @Test
    void managerCanUpdateDeploymentFeatureOverride() throws Exception {
        when(featureService.updateDeployment(any(), any(), any())).thenReturn(resolution(false, FeatureResolutionSource.TENANT));

        mockMvc.perform(put("/api/v1/features/AGE_VERIFICATION/deployment")
                        .with(user("manager").authorities(new SimpleGrantedAuthority("FEATURE_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.source").value("TENANT"));
    }

    private static FeatureResolutionResponse resolution(boolean enabled, FeatureResolutionSource source) {
        return new FeatureResolutionResponse(
                new FeatureDefinitionResponse(
                        FEATURE_ID,
                        FeatureCode.AGE_VERIFICATION,
                        "Age verification",
                        "Enable age-verification prompts.",
                        true,
                        Instant.parse("2026-07-28T12:00:00Z"),
                        Instant.parse("2026-07-28T12:00:00Z"),
                        0),
                enabled,
                source,
                null,
                null,
                null,
                null,
                null);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            JpaRepositoriesAutoConfiguration.class
    })
    @EnableMethodSecurity
    @Import({FeatureController.class, AuthorizationService.class, TestSecurityConfig.class})
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
