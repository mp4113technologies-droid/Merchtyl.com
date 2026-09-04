package com.merchtyl.auth;

import com.merchtyl.platform.admin.PlatformAuthorityService;
import com.merchtyl.platform.admin.PlatformUserRepository;
import com.merchtyl.logging.LoggingMdc;
import com.merchtyl.security.AccountScope;
import com.merchtyl.security.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.cors.CorsUtils;

import java.io.IOException;
import java.time.Instant;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final PlatformUserRepository platformUserRepository;
    private final PlatformAuthorityService platformAuthorityService;
    private final UserRepository userRepository;

    @Autowired
    public JwtAuthenticationFilter(
            JwtService jwtService,
            UserDetailsService userDetailsService,
            PlatformUserRepository platformUserRepository,
            PlatformAuthorityService platformAuthorityService,
            UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.platformUserRepository = platformUserRepository;
        this.platformAuthorityService = platformAuthorityService;
        this.userRepository = userRepository;
    }

    public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService) {
        this(jwtService, userDetailsService, null, null, null);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return CorsUtils.isPreFlightRequest(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = authorization.substring("Bearer ".length());
            String subject = jwtService.accessSubject(token);
            if (subject != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                AccountScope accountScope = jwtService.accessAccountScope(token);
                var userDetails = accountScope == AccountScope.PLATFORM
                        ? platformUserDetails(subject)
                        : userDetailsService.loadUserByUsername(subject);
                if (!userDetails.isEnabled() || !userDetails.isAccountNonLocked()) {
                    throw new org.springframework.security.authentication.LockedException("Account is unavailable");
                }
                if (accountScope == AccountScope.TENANT && userRepository != null) {
                    Instant issuedAt = jwtService.accessIssuedAt(token);
                    userRepository.findByEmailIgnoreCase(subject).ifPresent(user -> {
                        if (user.getPasswordResetAt() != null && issuedAt.isBefore(user.getPasswordResetAt())) {
                            throw new org.springframework.security.authentication.CredentialsExpiredException("Credentials changed");
                        }
                    });
                }
                var authentication = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                updateMdc(subject, accountScope);
            }
        } catch (RuntimeException exception) {
            log.warn("authentication_failed reason=invalid_or_expired_token uri={} method={}",
                    request.getRequestURI(),
                    request.getMethod());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private void updateMdc(String email, AccountScope accountScope) {
        LoggingMdc.putIfNotBlank(LoggingMdc.USERNAME, email);
        if (accountScope == AccountScope.PLATFORM) {
            if (platformUserRepository != null) {
                platformUserRepository.findByEmail(email).ifPresent(user ->
                        LoggingMdc.putIfNotBlank(LoggingMdc.USER_ID, user.id()));
            }
            return;
        }
        if (userRepository != null) {
            userRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
                LoggingMdc.putIfNotBlank(LoggingMdc.USER_ID, user.getId());
                LoggingMdc.putIfNotBlank(LoggingMdc.TENANT_ID, user.getTenantId());
            });
        }
    }

    private org.springframework.security.core.userdetails.UserDetails platformUserDetails(String email) {
        if (platformUserRepository == null || platformAuthorityService == null) {
            throw new RuntimeException("Platform authentication is unavailable");
        }
        var platformUser = platformUserRepository.findByEmail(email)
                .filter(user -> user.enabled() && !user.locked())
                .orElseThrow(() -> new RuntimeException("Platform user not found"));
        return User.withUsername(platformUser.email())
                .password(platformUser.passwordHash())
                .authorities(platformAuthorityService.authorities(platformUser))
                .disabled(!platformUser.enabled())
                .accountLocked(platformUser.locked())
                .build();
    }
}
