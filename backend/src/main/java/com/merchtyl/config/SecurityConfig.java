package com.merchtyl.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.merchtyl.auth.JwtAuthenticationFilter;
import com.merchtyl.common.ApiError;
import com.merchtyl.platform.web.CorrelationIdFilter;
import com.merchtyl.platform.testing.TestUserProvisioningProperties;
import com.merchtyl.portal.MerchantContextFilter;
import com.merchtyl.portal.MerchantPortalProperties;
import com.merchtyl.security.AuthRateLimitingFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({
        JwtProperties.class,
        SecurityProperties.class,
        PlatformAdministrationProperties.class,
        TestUserProvisioningProperties.class
        , MerchantPortalProperties.class,
        CorsProperties.class
})
public class SecurityConfig {
    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            ObjectProvider<MerchantContextFilter> merchantContextFilterProvider,
            ObjectMapper objectMapper,
            SecurityProperties securityProperties,
            CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
                                        + "img-src 'self' data: blob:; font-src 'self' data:; connect-src 'self'; "
                                        + "object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'"))
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .preload(true)
                                .maxAgeInSeconds(31_536_000))
                        .permissionsPolicy(permissions -> permissions.policy(
                                "camera=(), microphone=(), geolocation=(), payment=()")))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> writeSecurityError(
                                request,
                                response,
                                objectMapper,
                                HttpStatus.UNAUTHORIZED,
                                "unauthorized",
                                "Authentication is required"))
                        .accessDeniedHandler((request, response, exception) -> writeSecurityError(
                                request,
                                response,
                                objectMapper,
                                HttpStatus.FORBIDDEN,
                                "forbidden",
                                "Access is denied")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/password-policy").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/public/merchant-portals/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/reset-password",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout",
                                "/api/v1/auth/first-login/change-password",
                                "/api/v1/platform/auth/login",
                                "/api/v1/platform/admins/activate",
                                "/api/v1/platform/owner-invitations/activate",
                                "/api/v1/testing/users",
                                "/api/v1/testing/users/batch",
                                "/api/v1/testing/users/cleanup").permitAll()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/testing/users").permitAll()
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/v3/api-docs.yaml").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(
                        new AuthRateLimitingFilter(securityProperties, objectMapper),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        MerchantContextFilter merchantContextFilter = merchantContextFilterProvider.getIfAvailable();
        if (merchantContextFilter != null) http.addFilterAfter(merchantContextFilter, JwtAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.exactOrigins());
        configuration.setAllowedOriginPatterns(corsProperties.originPatterns());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Accept",
                "Authorization",
                "Content-Type",
                "X-Merchant-Slug",
                "Idempotency-Key",
                CorrelationIdFilter.HEADER_NAME));
        configuration.setExposedHeaders(List.of(CorrelationIdFilter.HEADER_NAME));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        log.info("cors_configuration_loaded exact_origins={} origin_patterns={}",
                corsProperties.exactOrigins(), corsProperties.originPatterns());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    AuthenticationProvider authenticationProvider(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private static void writeSecurityError(
            HttpServletRequest request,
            HttpServletResponse response,
            ObjectMapper objectMapper,
            HttpStatus status,
            String code,
            String message) throws IOException {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = request.getHeader(CorrelationIdFilter.HEADER_NAME);
        }

        if (status == HttpStatus.UNAUTHORIZED) {
            log.warn("authentication_event event=Authentication Required endpoint={} method={} reason={}",
                    request.getRequestURI(),
                    request.getMethod(),
                    code);
        } else if (status == HttpStatus.FORBIDDEN) {
            log.warn("authorization_event event=Access Denied user={} tenant={} permission={} endpoint={} method={}",
                    org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication() == null
                            ? ""
                            : org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName(),
                    MDC.get("tenantId"),
                    "",
                    request.getRequestURI(),
                    request.getMethod());
        }

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new ApiError(
                code,
                message,
                status.value(),
                request.getRequestURI(),
                request.getMethod(),
                correlationId,
                List.of(),
                Instant.now()));
    }
}
