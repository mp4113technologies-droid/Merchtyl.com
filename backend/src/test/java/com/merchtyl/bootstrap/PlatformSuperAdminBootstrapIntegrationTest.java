package com.merchtyl.bootstrap;

import com.merchtyl.MerchtylApplication;
import com.merchtyl.platform.admin.PlatformBootstrapRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = MerchtylApplication.class, properties = {
        "merchtyl.platform.bootstrap.enabled=true",
        "merchtyl.platform.bootstrap.email=bootstrap-admin@example.test",
        "merchtyl.platform.bootstrap.name=Bootstrap Administrator",
        "merchtyl.platform.bootstrap.password=BootstrapTest1!"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "MERCHTYL_RUN_LOCAL_PG", matches = "true")
class PlatformSuperAdminBootstrapIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired PlatformBootstrapRunner bootstrapRunner;
    @Autowired MockMvc mockMvc;

    @Test
    void createsEncodedLoginCapableAdminOnceAcrossRepeatedBootstrapRuns() throws Exception {
        assertThat(countAdmins()).isEqualTo(1);
        String hash = jdbc.queryForObject(
                "select password_hash from platform_users where email = 'bootstrap-admin@example.test'", String.class);
        assertThat(hash).isNotEqualTo("BootstrapTest1!");
        assertThat(passwordEncoder.matches("BootstrapTest1!", hash)).isTrue();

        bootstrapRunner.run(new DefaultApplicationArguments());
        bootstrapRunner.run(new DefaultApplicationArguments());
        assertThat(countAdmins()).isEqualTo(1);

        mockMvc.perform(post("/api/v1/platform/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"bootstrap-admin@example.test","password":"BootstrapTest1!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("PLATFORM_SUPER_ADMIN"));
    }

    private long countAdmins() {
        Long count = jdbc.queryForObject(
                "select count(*) from platform_users where role = 'PLATFORM_SUPER_ADMIN'", Long.class);
        return count == null ? 0 : count;
    }
}
