package com.merchtyl.auth;

import com.merchtyl.config.JwtProperties;
import com.merchtyl.security.RoleName;
import com.merchtyl.security.User;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {
    private static final String SECRET = "jwt-service-test-secret-change-before-production";
    private final JwtProperties properties = new JwtProperties("merchtyl-test", SECRET, 15, 7);
    private final JwtService jwtService = new JwtService(properties);

    @Test
    void issuedAccessTokenResolvesAccessSubject() {
        User user = new User("owner@example.local", "Owner Dev", "hash");
        Instant now = Instant.now();

        String token = jwtService.issueAccessToken(
                user,
                List.of(RoleName.OWNER),
                now,
                now.plusSeconds(900));

        assertThat(jwtService.accessSubject(token)).isEqualTo("owner@example.local");
    }

    @Test
    void rejectsJwtWithoutAccessTokenType() {
        Instant now = Instant.now();
        String token = Jwts.builder()
                .issuer(properties.issuer())
                .subject("owner@example.local")
                .claim("typ", "refresh")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(900)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThatThrownBy(() -> jwtService.accessSubject(token))
                .isInstanceOf(JwtException.class);
    }
}
