package com.merchtyl.tax;

import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.common.ConflictException;
import com.merchtyl.product.Product;
import com.merchtyl.product.ProductRepository;
import com.merchtyl.product.ProductValues;
import com.merchtyl.product.SellableType;
import com.merchtyl.security.UserRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaxGroupCategoryServiceTest {
    private final TaxGroupRepository taxGroupRepository = mock(TaxGroupRepository.class);
    private final TaxGroupComponentRepository taxGroupComponentRepository = mock(TaxGroupComponentRepository.class);
    private final TaxComponentRepository taxComponentRepository = mock(TaxComponentRepository.class);
    private final TaxCategoryRepository taxCategoryRepository = mock(TaxCategoryRepository.class);
    private final ProductTaxCategoryAssignmentRepository assignmentRepository = mock(ProductTaxCategoryAssignmentRepository.class);
    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuditService auditService = mock(AuditService.class);

    private final TaxGroupService taxGroupService = new TaxGroupService(taxGroupRepository, userRepository, auditService);
    private final TaxCategoryService taxCategoryService = new TaxCategoryService(taxCategoryRepository, taxGroupService, userRepository, auditService);
    private final ProductTaxCategoryAssignmentService assignmentService = new ProductTaxCategoryAssignmentService(
            assignmentRepository,
            productRepository,
            taxCategoryService,
            userRepository,
            auditService);

    @Test
    void createTaxGroupNormalizesCodeAndAudits() {
        when(taxGroupRepository.existsByCodeIgnoreCase("CA-HST")).thenReturn(false);
        when(taxGroupRepository.saveAndFlush(any(TaxGroup.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaxGroupResponse response = taxGroupService.create(new TaxGroupRequest(" ca-hst ", " HST group ", null, true), null);

        assertThat(response.code()).isEqualTo("CA-HST");
        assertThat(response.name()).isEqualTo("HST group");
        verify(auditService).record(any(CreateAuditRecordCommand.class));
    }

    @Test
    void createTaxCategoryRejectsDuplicateCode() {
        when(taxCategoryRepository.existsByCodeIgnoreCase("STANDARD")).thenReturn(true);

        assertThatThrownBy(() -> taxCategoryService.create(new TaxCategoryRequest(null, "standard", "Standard", TaxTreatment.STANDARD, null, true), null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Tax category code already exists");

        verify(taxCategoryRepository, never()).saveAndFlush(any());
    }

    @Test
    void createTaxCategoryAllowsOptionalTaxGroupAndTreatment() {
        when(taxCategoryRepository.existsByCodeIgnoreCase("EXEMPT")).thenReturn(false);
        when(taxCategoryRepository.saveAndFlush(any(TaxCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaxCategoryResponse response = taxCategoryService.create(new TaxCategoryRequest(null, " exempt ", " Exempt ", TaxTreatment.EXEMPT, " No tax ", true), null);

        assertThat(response.taxGroupId()).isNull();
        assertThat(response.code()).isEqualTo("EXEMPT");
        assertThat(response.treatment()).isEqualTo(TaxTreatment.EXEMPT);
        verify(auditService).record(any(CreateAuditRecordCommand.class));
    }

    @Test
    void productAssignmentSetsProductTaxCategoryPlaceholder() {
        Product product = product();
        TaxCategory category = new TaxCategory(null, "STANDARD", "Standard", TaxTreatment.STANDARD, null, true);
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(taxCategoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(assignmentRepository.existsByProduct(product)).thenReturn(false);
        when(assignmentRepository.saveAndFlush(any(ProductTaxCategoryAssignment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductTaxCategoryAssignmentResponse response = assignmentService.create(new ProductTaxCategoryAssignmentRequest(product.getId(), category.getId(), true), null);

        assertThat(response.productId()).isEqualTo(product.getId());
        assertThat(response.taxCategoryId()).isEqualTo(category.getId());
        assertThat(product.getTaxCategoryId()).isEqualTo(category.getId());
        verify(auditService).record(any(CreateAuditRecordCommand.class));
    }

    @Test
    void productAssignmentRejectsSecondAssignmentForProduct() {
        Product product = product();
        TaxCategory category = new TaxCategory(null, "STANDARD", "Standard", TaxTreatment.STANDARD, null, true);
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(taxCategoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(assignmentRepository.existsByProduct(product)).thenReturn(true);

        assertThatThrownBy(() -> assignmentService.create(new ProductTaxCategoryAssignmentRequest(product.getId(), category.getId(), true), null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Product already has");

        verify(assignmentRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateTaxGroupRequiresCurrentVersion() {
        TaxGroup group = new TaxGroup("CA-HST", "HST group", null, true);
        when(taxGroupRepository.findById(group.getId())).thenReturn(Optional.of(group));

        assertThatThrownBy(() -> taxGroupService.update(group.getId(), new TaxGroupUpdateRequest("CA-HST", "HST group", null, true, group.getVersion() + 1), null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Tax group was modified");

        verify(taxGroupRepository, never()).saveAndFlush(any());
    }

    private static Product product() {
        return new Product(new ProductValues(
                "SKU-1",
                "Coffee",
                null,
                SellableType.STANDARD_PRODUCT,
                null,
                BigDecimal.ONE,
                BigDecimal.TEN,
                null,
                null,
                true,
                true,
                false,
                null,
                null,
                List.of(),
                List.of(),
                Set.of()));
    }
}
