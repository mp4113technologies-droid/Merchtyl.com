package com.merchtyl.catalogue;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class MerchantIdentifierGenerator {
    private final EntityManager entityManager;

    public MerchantIdentifierGenerator(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public String nextCatalogueCode(UUID tenantId, String type) {
        Number sequence = next(tenantId, type, "");
        String prefix = (String) entityManager.createNativeQuery(
                        "select identifier_prefix from tenants where id = :tenantId")
                .setParameter("tenantId", tenantId)
                .getSingleResult();
        return "%s%s%03d".formatted(prefix, type.equals("CATEGORY") ? "CAT" : "BR", sequence.longValue());
    }

    public long nextSkuSequence(UUID tenantId, String base) {
        return next(tenantId, "SKU", base).longValue();
    }

    private Number next(UUID tenantId, String namespace, String key) {
        return (Number) entityManager.createNativeQuery(
                        "select next_tenant_identifier_sequence(:tenantId, :namespace, :key)")
                .setParameter("tenantId", tenantId)
                .setParameter("namespace", namespace)
                .setParameter("key", key)
                .getSingleResult();
    }
}
