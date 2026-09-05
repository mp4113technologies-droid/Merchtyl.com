package com.merchtyl.portal;

import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.ConflictException;
import com.merchtyl.common.NotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class MerchantPortalService {
    public static final String HEADER_NAME = "X-Merchant-Slug";
    public static final Set<String> RESERVED = Set.of("www", "api", "platform", "admin", "app", "portal", "login", "logout", "signup", "support", "help", "status", "billing", "docs", "assets", "static", "mail", "cdn");
    private final JdbcTemplate jdbcTemplate;
    private final MerchantPortalProperties properties;

    public MerchantPortalService(JdbcTemplate jdbcTemplate, MerchantPortalProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    public String normalize(String value) {
        String slug = Normalizer.normalize(value == null ? "" : value.trim(), Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("-+", "-").replaceAll("^-|-$", "");
        if (slug.isBlank()) slug = "merchant";
        if (slug.length() > 63) slug = slug.substring(0, 63).replaceAll("-$", "");
        return slug;
    }

    public void validate(String slug) {
        if (slug == null || !slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*") || slug.length() > 63) {
            throw new BadRequestException("MERCHANT_SLUG_INVALID");
        }
        if (RESERVED.contains(slug)) throw new BadRequestException("MERCHANT_SLUG_RESERVED");
    }

    public String nextAvailableSlug(String displayName) {
        String base = normalize(displayName);
        if (RESERVED.contains(base)) base = trimForSuffix(base, "merchant") + "-merchant";
        String candidate = base;
        int suffix = 2;
        while (exists(candidate)) candidate = trimForSuffix(base, Integer.toString(suffix)) + "-" + suffix++;
        return candidate;
    }

    public String portalUrl(String slug) {
        validate(slug);
        String scheme = "localhost".equals(properties.publicBaseDomain()) ? "http" : "https";
        return scheme + "://" + slug + "." + properties.publicBaseDomain();
    }

    public PublicMerchantPortalResponse resolve(String slug) {
        validate(slug);
        return jdbcTemplate.query("select merchant_slug, display_name, status from tenants where merchant_slug = ?",
                (rs, row) -> new PublicMerchantPortalResponse(rs.getString(1), rs.getString(2), available(rs.getString(3))), slug)
                .stream().findFirst().orElseThrow(() -> new NotFoundException("MERCHANT_PORTAL_NOT_FOUND"));
    }

    public UUID tenantId(String slug) {
        validate(slug);
        return jdbcTemplate.query("select id from tenants where merchant_slug = ?", (rs, row) -> rs.getObject(1, UUID.class), slug)
                .stream().findFirst().orElseThrow(() -> new NotFoundException("MERCHANT_PORTAL_NOT_FOUND"));
    }

    public void translateUniqueViolation(DataIntegrityViolationException exception) {
        throw new ConflictException("MERCHANT_SLUG_ALREADY_EXISTS");
    }

    private boolean exists(String slug) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("select exists(select 1 from tenants where merchant_slug=?)", Boolean.class, slug));
    }

    private static String trimForSuffix(String base, String suffix) {
        return base.substring(0, Math.min(base.length(), 62 - suffix.length())).replaceAll("-$", "");
    }

    private static boolean available(String status) {
        return "ACTIVE".equals(status) || "PENDING_OWNER_ACTIVATION".equals(status) || "PENDING_ONBOARDING".equals(status);
    }
}
