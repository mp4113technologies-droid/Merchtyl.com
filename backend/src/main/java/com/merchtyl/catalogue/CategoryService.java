package com.merchtyl.catalogue;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.security.UserRepository;
import com.merchtyl.security.StoreAccessService;
import org.springframework.stereotype.Service;

@Service
public class CategoryService extends CatalogueReferenceService<Category> {
    public CategoryService(CategoryRepository repository, UserRepository userRepository, AuditService auditService,
                           StoreAccessService storeAccessService, MerchantIdentifierGenerator identifierGenerator) {
        super(
                repository,
                repository,
                repository,
                userRepository,
                auditService,
                values -> new Category(values.code(), values.name(), values.description(), values.active()),
                new CatalogueReferenceAuditActions(
                        AuditAction.CATEGORY_CREATED,
                        AuditAction.CATEGORY_UPDATED,
                        AuditAction.CATEGORY_STATUS_CHANGED),
                "CATEGORY",
                "Category",
                repository,
                storeAccessService,
                identifierGenerator);
    }
}
