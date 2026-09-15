package com.merchtyl.catalogue;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.security.UserRepository;
import com.merchtyl.security.StoreAccessService;
import com.merchtyl.common.ConflictException;
import com.merchtyl.common.NotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CategoryService extends CatalogueReferenceService<Category> {
    private final CategoryRepository repository;
    private final StoreAccessService storeAccessService;

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
        this.repository = repository;
        this.storeAccessService = storeAccessService;
    }

    @Override
    @Transactional
    public CatalogueReferenceResponse update(UUID id, CatalogueReferenceUpdateRequest request, Authentication authentication) {
        requireMutable(id, authentication);
        return super.update(id, request, authentication);
    }

    @Override
    @Transactional
    public CatalogueReferenceResponse updateStatus(UUID id, CatalogueReferenceStatusRequest request, Authentication authentication) {
        Category category = category(id, authentication);
        if (category.isSystemManaged() && !request.active()) {
            throw new ConflictException("SYSTEM_CATEGORY_PROTECTED");
        }
        return super.updateStatus(id, request, authentication);
    }

    private void requireMutable(UUID id, Authentication authentication) {
        if (category(id, authentication).isSystemManaged()) {
            throw new ConflictException("SYSTEM_CATEGORY_PROTECTED");
        }
    }

    private Category category(UUID id, Authentication authentication) {
        UUID tenantId = storeAccessService.currentTenantId(authentication);
        return repository.findById(id)
                .filter(category -> tenantId.equals(category.getTenantId()))
                .orElseThrow(() -> new NotFoundException("Category not found"));
    }
}
