package com.merchtyl.catalogue;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.common.ConflictException;
import com.merchtyl.security.User;
import com.merchtyl.security.UserRepository;
import com.merchtyl.security.StoreAccessService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.Authentication;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CatalogueReferenceServiceTest {
    private final CategoryRepository categoryRepository = mock(CategoryRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final StoreAccessService storeAccessService = mock(StoreAccessService.class);
    private final MerchantIdentifierGenerator identifierGenerator = mock(MerchantIdentifierGenerator.class);
    private final CategoryService service = new CategoryService(categoryRepository, userRepository, auditService,
            storeAccessService, identifierGenerator);
    private final UUID tenantId = UUID.randomUUID();

    @Test
    void createNormalizesCodeAndAuditsCreation() {
        User actor = new User("manager@example.local", "Manager", "hash");
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("manager@example.local");
        when(storeAccessService.currentTenantId(authentication)).thenReturn(tenantId);
        when(identifierGenerator.nextCatalogueCode(tenantId, "CATEGORY")).thenReturn("SSCAT001");
        when(userRepository.findByEmailIgnoreCase("manager@example.local")).thenReturn(Optional.of(actor));
        when(categoryRepository.existsByTenantIdAndCodeIgnoreCase(tenantId, "SSCAT001")).thenReturn(false);
        when(categoryRepository.saveAndFlush(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CatalogueReferenceResponse response = service.create(new CatalogueReferenceRequest(
                null,
                " Grocery ",
                " General grocery items ",
                true), authentication);

        assertThat(response.code()).isEqualTo("SSCAT001");
        assertThat(response.name()).isEqualTo("Grocery");
        assertThat(response.description()).isEqualTo("General grocery items");

        ArgumentCaptor<CreateAuditRecordCommand> audit = ArgumentCaptor.forClass(CreateAuditRecordCommand.class);
        verify(auditService).record(audit.capture());
        assertThat(audit.getValue().actorUserId()).isEqualTo(actor.getId());
        assertThat(audit.getValue().action()).isEqualTo(AuditAction.CATEGORY_CREATED);
        assertThat(audit.getValue().entityType()).isEqualTo("CATEGORY");
        assertThat(audit.getValue().afterSnapshot().toString()).contains("SSCAT001");
    }

    @Test
    void createRejectsDuplicateCodeBeforeSaving() {
        Authentication authentication = mock(Authentication.class);
        when(storeAccessService.currentTenantId(authentication)).thenReturn(tenantId);
        when(identifierGenerator.nextCatalogueCode(tenantId, "CATEGORY")).thenReturn("SSCAT001");
        when(categoryRepository.existsByTenantIdAndCodeIgnoreCase(tenantId, "SSCAT001")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CatalogueReferenceRequest(
                "grocery",
                "Grocery",
                null,
                true), authentication))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("code already exists");

        verify(categoryRepository, never()).saveAndFlush(any());
        verify(auditService, never()).record(any());
    }

    @Test
    void updateRequiresCurrentVersion() {
        Category category = new Category("GROCERY", "Grocery", null, true);
        category.assignTenant(tenantId);
        Authentication authentication = mock(Authentication.class);
        when(storeAccessService.currentTenantId(authentication)).thenReturn(tenantId);
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> service.update(category.getId(), new CatalogueReferenceUpdateRequest(
                "GROCERY",
                "Grocery",
                null,
                true,
                category.getVersion() + 1), authentication))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("modified by another transaction");

        verify(categoryRepository, never()).saveAndFlush(any());
        verify(auditService, never()).record(any());
    }
}
