package com.merchtyl.platform.admin;

import com.merchtyl.config.JwtProperties;
import com.merchtyl.config.PlatformAdministrationProperties;
import com.merchtyl.config.SecurityProperties;
import com.merchtyl.email.EmailProperties;
import com.merchtyl.reference.ReferenceDataService;
import com.merchtyl.security.RefreshTokenService;
import com.merchtyl.security.TemporaryPasswordGenerator;
import com.merchtyl.security.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlatformAdministrationServiceActivationLinkExposureTest {

    @Test
    void devProfileAllowsTransientActivationLinkExposure() throws Exception {
        PlatformAdministrationService service = serviceWithProfiles("dev");

        assertThat(canExposeActivationLinks(service)).isTrue();
    }

    @Test
    void productionProfileBlocksTransientActivationLinkExposureEvenWhenDevIsAlsoActive() throws Exception {
        PlatformAdministrationService service = serviceWithProfiles("dev", "prod");

        assertThat(canExposeActivationLinks(service)).isFalse();
    }

    private static boolean canExposeActivationLinks(PlatformAdministrationService service) throws Exception {
        Method method = PlatformAdministrationService.class.getDeclaredMethod("canExposeActivationLinks");
        method.setAccessible(true);
        return (boolean) method.invoke(service);
    }

    private static PlatformAdministrationService serviceWithProfiles(String... profiles) {
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(profiles);
        return new PlatformAdministrationService(
                mock(JdbcTemplate.class),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                mock(PlatformUserRepository.class),
                mock(UserRepository.class),
                mock(PasswordEncoder.class),
                mock(com.merchtyl.auth.JwtService.class),
                mock(RefreshTokenService.class),
                new JwtProperties("issuer", "secret-secret-secret-secret-secret-secret", 15, 7),
                new PlatformAdministrationProperties(new PlatformAdministrationProperties.Bootstrap(true,
                        "platform@example.local", "Platform Admin", "ChangeMe123!"),
                        new PlatformAdministrationProperties.OwnerInvitation(48, 3, 60, 10),
                        new PlatformAdministrationProperties.SupportAccess(false, 30)),
                mock(com.merchtyl.audit.AuditService.class),
                mock(ReferenceDataService.class),
                mock(ApplicationEventPublisher.class),
                new EmailProperties("resend", "noreply-merchtyl@gmail.com", "Merchtyl", "",
                        "http://localhost:5173", new EmailProperties.Retry(5, 30, 2),
                        new EmailProperties.Resend(true, "configured-key")),
                environment,
                mock(TemporaryPasswordGenerator.class),
                new SecurityProperties(null, null, null));
    }
}
