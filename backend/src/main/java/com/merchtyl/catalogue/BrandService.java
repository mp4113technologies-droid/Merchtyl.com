package com.merchtyl.catalogue;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.security.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class BrandService extends CatalogueReferenceService<Brand> {
    public BrandService(BrandRepository repository, UserRepository userRepository, AuditService auditService) {
        super(
                repository,
                repository,
                repository,
                userRepository,
                auditService,
                values -> new Brand(values.code(), values.name(), values.description(), values.active()),
                new CatalogueReferenceAuditActions(
                        AuditAction.BRAND_CREATED,
                        AuditAction.BRAND_UPDATED,
                        AuditAction.BRAND_STATUS_CHANGED),
                "BRAND",
                "Brand");
    }
}
