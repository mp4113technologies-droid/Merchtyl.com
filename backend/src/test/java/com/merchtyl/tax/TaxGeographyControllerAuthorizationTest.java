package com.merchtyl.tax;

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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
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

@SpringBootTest(classes = TaxGeographyControllerAuthorizationTest.TestApplication.class)
@AutoConfigureMockMvc
class TaxGeographyControllerAuthorizationTest {
    private static final UUID COUNTRY_ID = UUID.fromString("00000000-0000-0000-0000-000000001001");
    private static final UUID AREA_ID = UUID.fromString("00000000-0000-0000-0000-000000001002");
    private static final UUID JURISDICTION_ID = UUID.fromString("00000000-0000-0000-0000-000000001003");

    @Autowired
    MockMvc mockMvc;

    @MockBean
    CountryService countryService;

    @MockBean
    AdministrativeAreaService administrativeAreaService;

    @MockBean
    TaxJurisdictionService taxJurisdictionService;

    @Test
    void taxViewerCannotCreateCountry() throws Exception {
        mockMvc.perform(post("/api/v1/tax/countries")
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("TAX_VIEW")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(countryJson()))
                .andExpect(status().isForbidden());

        verify(countryService, never()).create(any(), any());
    }

    @Test
    void taxManagerCanCreateCountry() throws Exception {
        when(countryService.create(any(), any())).thenReturn(countryResponse());

        mockMvc.perform(post("/api/v1/tax/countries")
                        .with(user("manager").authorities(new SimpleGrantedAuthority("TAX_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(countryJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(COUNTRY_ID.toString()))
                .andExpect(jsonPath("$.code").value("CA"));
    }

    @Test
    void taxViewerCanListAllTaxGeographyResources() throws Exception {
        when(countryService.search(any())).thenReturn(new PageResponse<>(List.of(countryResponse()), 0, 20, 1, 1, true, true));
        when(administrativeAreaService.search(any())).thenReturn(new PageResponse<>(List.of(areaResponse()), 0, 20, 1, 1, true, true));
        when(taxJurisdictionService.search(any())).thenReturn(new PageResponse<>(List.of(jurisdictionResponse()), 0, 20, 1, 1, true, true));

        mockMvc.perform(get("/api/v1/tax/countries")
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("TAX_VIEW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].code").value("CA"));
        mockMvc.perform(get("/api/v1/tax/administrative-areas")
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("TAX_VIEW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].code").value("NB"));
        mockMvc.perform(get("/api/v1/tax/jurisdictions")
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("TAX_VIEW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].code").value("GST"));
    }

    @Test
    void statusPatchRequiresTaxManagePermission() throws Exception {
        mockMvc.perform(patch("/api/v1/tax/jurisdictions/{id}/status", JURISDICTION_ID)
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("TAX_VIEW")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "active": false,
                                  "version": 0
                                }
                                """))
                .andExpect(status().isForbidden());

        verify(taxJurisdictionService, never()).updateStatus(any(), any(), any());
    }

    private static String countryJson() {
        return """
                {
                  "code": "CA",
                  "name": "Canada",
                  "active": true
                }
                """;
    }

    private static CountryResponse countryResponse() {
        return new CountryResponse(
                COUNTRY_ID,
                "CA",
                "Canada",
                true,
                Instant.parse("2026-07-22T12:00:00Z"),
                Instant.parse("2026-07-22T12:00:00Z"),
                0);
    }

    private static AdministrativeAreaResponse areaResponse() {
        return new AdministrativeAreaResponse(
                AREA_ID,
                COUNTRY_ID,
                "NB",
                "New Brunswick",
                AdministrativeAreaType.PROVINCE,
                true,
                Instant.parse("2026-07-22T12:00:00Z"),
                Instant.parse("2026-07-22T12:00:00Z"),
                0);
    }

    private static TaxJurisdictionResponse jurisdictionResponse() {
        return new TaxJurisdictionResponse(
                JURISDICTION_ID,
                COUNTRY_ID,
                null,
                "GST",
                "GST",
                TaxJurisdictionType.NATIONAL,
                true,
                Instant.parse("2026-07-22T12:00:00Z"),
                Instant.parse("2026-07-22T12:00:00Z"),
                0);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            JpaRepositoriesAutoConfiguration.class
    })
    @EnableMethodSecurity
    @Import({CountryController.class, AdministrativeAreaController.class, TaxJurisdictionController.class, AuthorizationService.class, TestSecurityConfig.class})
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
