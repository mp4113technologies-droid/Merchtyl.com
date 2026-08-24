package com.merchtyl.auth;

import com.merchtyl.config.JwtProperties;
import com.merchtyl.platform.admin.PlatformUserAccount;
import com.merchtyl.security.AccountScope;
import com.merchtyl.security.RoleName;
import com.merchtyl.security.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Service
public class JwtService {
    private final JwtProperties jwtProperties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.signingKey = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String issueAccessToken(User user, List<RoleName> roles, Instant issuedAt, Instant expiresAt) {
        return Jwts.builder()
                .issuer(jwtProperties.issuer())
                .subject(user.getEmail())
                .claim("uid", user.getId().toString())
                .claim("accountScope", AccountScope.TENANT.name())
                .claim("tenantId", user.getTenantId() == null ? null : user.getTenantId().toString())
                .claim("roles", roles.stream().map(RoleName::name).toList())
                .claim("typ", "access")
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    public String issuePlatformAccessToken(PlatformUserAccount user, Instant issuedAt, Instant expiresAt) {
        return Jwts.builder()
                .issuer(jwtProperties.issuer())
                .subject(user.email())
                .claim("uid", user.id().toString())
                .claim("accountScope", AccountScope.PLATFORM.name())
                .claim("role", user.role().name())
                .claim("roles", List.of(user.role().name()))
                .claim("typ", "access")
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    public String accessSubject(String token) {
        Claims claims = claims(token);
        if (!"access".equals(claims.get("typ", String.class))) {
            throw new JwtException("JWT token type is not supported");
        }
        return claims.getSubject();
    }

    public AccountScope accessAccountScope(String token) {
        Claims claims = claims(token);
        if (!"access".equals(claims.get("typ", String.class))) {
            throw new JwtException("JWT token type is not supported");
        }
        String scope = claims.get("accountScope", String.class);
        return scope == null ? AccountScope.TENANT : AccountScope.valueOf(scope);
    }

    public Instant accessIssuedAt(String token) {
        Claims claims = claims(token);
        if (!"access".equals(claims.get("typ", String.class)) || claims.getIssuedAt() == null) {
            throw new JwtException("JWT issued-at claim is missing");
        }
        return claims.getIssuedAt().toInstant();
    }

    private Claims claims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(jwtProperties.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
