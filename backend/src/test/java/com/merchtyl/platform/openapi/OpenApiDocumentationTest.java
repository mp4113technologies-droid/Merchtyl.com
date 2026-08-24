package com.merchtyl.platform.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.merchtyl.auth.AuthController;
import com.merchtyl.auth.AuthService;
import com.merchtyl.auth.JwtAuthenticationFilter;
import com.merchtyl.auth.JwtService;
import com.merchtyl.config.SecurityConfig;
import com.merchtyl.eod.BusinessDayController;
import com.merchtyl.eod.BusinessDayService;
import com.merchtyl.eod.EndOfDayReportController;
import com.merchtyl.idempotency.IdempotencyService;
import com.merchtyl.lottery.LotteryPayoutController;
import com.merchtyl.lottery.LotteryPayoutService;
import com.merchtyl.lottery.LotterySaleController;
import com.merchtyl.lottery.LotterySaleService;
import com.merchtyl.refunds.RefundController;
import com.merchtyl.refunds.RefundService;
import com.merchtyl.sales.SaleController;
import com.merchtyl.sales.SaleService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = OpenApiDocumentationTest.TestApplication.class)
@AutoConfigureMockMvc
@Import({
        OpenApiConfiguration.class,
        SecurityConfig.class,
        AuthController.class,
        SaleController.class,
        RefundController.class,
        LotterySaleController.class,
        LotteryPayoutController.class,
        BusinessDayController.class,
        EndOfDayReportController.class
})
@TestPropertySource(properties = {
        "merchtyl.jwt.secret=test-secret-change-this-development-secret",
        "merchtyl.security.cors.allowed-origins=http://localhost:5173",
        "merchtyl.security.rate-limit.enabled=false",
        "springdoc.api-docs.enabled=true",
        "springdoc.api-docs.path=/v3/api-docs",
        "springdoc.swagger-ui.enabled=true",
        "springdoc.swagger-ui.path=/swagger-ui.html"
})
class OpenApiDocumentationTest {
    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class
    })
    static class TestApplication {
        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService) {
            return new JwtAuthenticationFilter(jwtService, userDetailsService);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    AuthService authService;

    @MockBean
    SaleService saleService;

    @MockBean
    RefundService refundService;

    @MockBean
    LotterySaleService lotterySaleService;

    @MockBean
    LotteryPayoutService lotteryPayoutService;

    @MockBean
    BusinessDayService businessDayService;

    @MockBean
    IdempotencyService idempotencyService;

    @MockBean
    JwtService jwtService;

    @MockBean
    UserDetailsService userDetailsService;

    @Test
    void swaggerUiRouteIsAvailableWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/swagger-ui/index.html"));

        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    @Test
    void openApiJsonAndYamlAreGeneratedWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("application/json")));

        mockMvc.perform(get("/v3/api-docs.yaml"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("application/vnd.oai.openapi")));
    }

    @Test
    void jwtSecuritySchemeExistsAndProtectedEndpointsRequireBearerAuthInDocs() throws Exception {
        JsonNode openApi = openApiJson();

        JsonNode bearer = openApi.path("components").path("securitySchemes").path("bearerAuth");
        assertThat(bearer.path("type").asText()).isEqualTo("http");
        assertThat(bearer.path("scheme").asText()).isEqualTo("bearer");
        assertThat(bearer.path("bearerFormat").asText()).isEqualTo("JWT");

        JsonNode completeSecurity = openApi.path("paths").path("/api/v1/sales/{id}/complete").path("post").path("security");
        assertThat(completeSecurity.toString()).contains("bearerAuth");

        JsonNode loginSecurity = openApi.path("paths").path("/api/v1/auth/login").path("post").path("security");
        assertThat(loginSecurity.isArray()).isTrue();
        assertThat(loginSecurity).isEmpty();
    }

    @Test
    void idempotentEndpointsDocumentRequiredIdempotencyKey() throws Exception {
        JsonNode openApi = openApiJson();

        assertHeaderParameter(openApi, "/api/v1/sales/{id}/complete");
        assertHeaderParameter(openApi, "/api/v1/refunds");
        assertHeaderParameter(openApi, "/api/v1/lottery/sales");
        assertHeaderParameter(openApi, "/api/v1/lottery/payouts/{id}/complete-cash");
        assertHeaderParameter(openApi, "/api/v1/lottery/payouts/{id}/reverse");
        assertHeaderParameter(openApi, "/api/v1/business-days/{id}/close");
    }

    @Test
    void sensitiveInternalsAreNotExposedInGeneratedSchemas() throws Exception {
        String document = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(document).doesNotContain("passwordHash");
        assertThat(document).doesNotContain("SPRING_DATASOURCE_PASSWORD");
        assertThat(document).doesNotContain("correct-horse-battery");
        assertThat(document).contains("<password>");
        assertThat(document).contains("<jwt-access-token>");
    }

    @Test
    void existingEndpointAuthorizationRemainsEnforced() throws Exception {
        mockMvc.perform(get("/api/v1/sales"))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode openApiJson() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body);
    }

    private static void assertHeaderParameter(JsonNode openApi, String path) {
        JsonNode parameters = openApi.path("paths").path(path).path("post").path("parameters");
        assertThat(parameters).anySatisfy(parameter -> {
            assertThat(parameter.path("name").asText()).isEqualTo("Idempotency-Key");
            assertThat(parameter.path("in").asText()).isEqualTo("header");
            assertThat(parameter.path("required").asBoolean()).isTrue();
        });
    }
}
