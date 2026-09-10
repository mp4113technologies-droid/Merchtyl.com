package com.merchtyl.product;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ProductReferenceGenerator {
    private final EntityManager entityManager;

    public ProductReferenceGenerator(EntityManager entityManager) { this.entityManager = entityManager; }

    @Transactional
    public String next(UUID tenantId) {
        return (String) entityManager.createNativeQuery("select next_product_reference(:tenantId)")
                .setParameter("tenantId", tenantId)
                .getSingleResult();
    }
}
