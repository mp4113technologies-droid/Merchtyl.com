package com.merchtyl.tax;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.ConflictException;
import com.merchtyl.security.UserRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaxComponentRateServiceTest {
    private final TaxTypeRepository taxTypeRepository = mock(TaxTypeRepository.class);
    private final TaxComponentRepository taxComponentRepository = mock(TaxComponentRepository.class);
    private final TaxJurisdictionRepository taxJurisdictionRepository = mock(TaxJurisdictionRepository.class);
    private final TaxRateRepository taxRateRepository = mock(TaxRateRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final TaxTypeService taxTypeService = new TaxTypeService(taxTypeRepository, userRepository, auditService);
    private final TaxComponentService taxComponentService = new TaxComponentService(
            taxComponentRepository,
            taxTypeService,
            taxJurisdictionRepository,
            userRepository,
            auditService);
    private final TaxRateService taxRateService = new TaxRateService(taxRateRepository, taxComponentService, userRepository, auditService);

    @Test
    void createTaxTypeNormalizesCodeAndAudits() {
        when(taxTypeRepository.existsByCodeIgnoreCase("SALES_TAX")).thenReturn(false);
        when(taxTypeRepository.saveAndFlush(any(TaxType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaxTypeResponse response = taxTypeService.create(new TaxTypeRequest(" sales_tax ", " Sales tax ", " Retail sales ", true), null);

        assertThat(response.code()).isEqualTo("SALES_TAX");
        assertThat(response.name()).isEqualTo("Sales tax");
        assertThat(response.description()).isEqualTo("Retail sales");
        verify(auditService).record(any(CreateAuditRecordCommand.class));
    }

    @Test
    void createTaxComponentValidatesTypeAndJurisdiction() {
        TaxType type = new TaxType("GST", "GST", null, true);
        Country country = new Country("CA", "Canada", true);
        TaxJurisdiction jurisdiction = new TaxJurisdiction(country, null, "GST", "GST", TaxJurisdictionType.NATIONAL, true);
        when(taxTypeRepository.findById(type.getId())).thenReturn(Optional.of(type));
        when(taxJurisdictionRepository.findById(jurisdiction.getId())).thenReturn(Optional.of(jurisdiction));
        when(taxComponentRepository.existsByCodeIgnoreCase("GST")).thenReturn(false);
        when(taxComponentRepository.saveAndFlush(any(TaxComponent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaxComponentResponse response = taxComponentService.create(new TaxComponentRequest(
                type.getId(),
                jurisdiction.getId(),
                " gst ",
                " Federal GST ",
                null,
                true), null);

        assertThat(response.taxTypeId()).isEqualTo(type.getId());
        assertThat(response.taxJurisdictionId()).isEqualTo(jurisdiction.getId());
        assertThat(response.code()).isEqualTo("GST");
        verify(auditService).record(any(CreateAuditRecordCommand.class));
    }

    @Test
    void createTaxRatePersistsPercentageFlagsStatusAndMetadata() {
        TaxComponent component = component();
        Instant verifiedAt = Instant.parse("2026-07-22T12:00:00Z");
        when(taxComponentRepository.findById(component.getId())).thenReturn(Optional.of(component));
        when(taxRateRepository.existsOverlappingActivePeriod(eq(component.getId()), any(), any(), any(), eq(null))).thenReturn(false);
        when(taxRateRepository.saveAndFlush(any(TaxRate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaxRateResponse response = taxRateService.create(new TaxRateRequest(
                component.getId(),
                new BigDecimal("15.000000"),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                true,
                true,
                2,
                TaxRateStatus.ACTIVE,
                "Revenue bulletin",
                "https://example.test/tax",
                "Tax Admin",
                verifiedAt), null);

        assertThat(response.percentageRate()).isEqualByComparingTo("15.000000");
        assertThat(response.includedInPrice()).isTrue();
        assertThat(response.compoundOnPreviousTax()).isTrue();
        assertThat(response.calculationOrder()).isEqualTo(2);
        assertThat(response.status()).isEqualTo(TaxRateStatus.ACTIVE);
        assertThat(response.source()).isEqualTo("Revenue bulletin");
        assertThat(response.verifiedAt()).isEqualTo(verifiedAt);
        verify(auditService).record(any(CreateAuditRecordCommand.class));
    }

    @Test
    void createTaxRateRejectsOverlappingActiveOrScheduledPeriod() {
        TaxComponent component = component();
        when(taxComponentRepository.findById(component.getId())).thenReturn(Optional.of(component));
        when(taxRateRepository.existsOverlappingActivePeriod(eq(component.getId()), any(), any(), any(), eq(null))).thenReturn(true);

        assertThatThrownBy(() -> taxRateService.create(new TaxRateRequest(
                component.getId(),
                new BigDecimal("8.250000"),
                LocalDate.of(2026, 7, 1),
                null,
                false,
                false,
                0,
                TaxRateStatus.SCHEDULED,
                null,
                null,
                null,
                null), null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("overlaps");

        verify(taxRateRepository, never()).saveAndFlush(any());
        verify(auditService, never()).record(any());
    }

    @Test
    void draftTaxRatesMayOverlap() {
        TaxComponent component = component();
        when(taxComponentRepository.findById(component.getId())).thenReturn(Optional.of(component));
        when(taxRateRepository.saveAndFlush(any(TaxRate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        taxRateService.create(new TaxRateRequest(
                component.getId(),
                new BigDecimal("8.250000"),
                LocalDate.of(2026, 7, 1),
                null,
                false,
                false,
                0,
                TaxRateStatus.DRAFT,
                null,
                null,
                null,
                null), null);

        verify(taxRateRepository, never()).existsOverlappingActivePeriod(any(), any(), any(), any(), any());
        verify(taxRateRepository).saveAndFlush(any(TaxRate.class));
    }

    @Test
    void createTaxRateRejectsInvalidEffectiveRange() {
        TaxComponent component = component();
        when(taxComponentRepository.findById(component.getId())).thenReturn(Optional.of(component));

        assertThatThrownBy(() -> taxRateService.create(new TaxRateRequest(
                component.getId(),
                BigDecimal.ONE,
                LocalDate.of(2026, 12, 31),
                LocalDate.of(2026, 1, 1),
                false,
                false,
                0,
                TaxRateStatus.ACTIVE,
                null,
                null,
                null,
                null), null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("effectiveTo");
    }

    @Test
    void updateTaxRateRequiresCurrentVersion() {
        TaxRate rate = new TaxRate(new TaxRateValues(
                component(),
                BigDecimal.ONE,
                LocalDate.of(2026, 1, 1),
                null,
                false,
                false,
                0,
                TaxRateStatus.ACTIVE,
                null,
                null,
                null,
                null));
        when(taxRateRepository.findById(rate.getId())).thenReturn(Optional.of(rate));

        assertThatThrownBy(() -> taxRateService.update(rate.getId(), new TaxRateUpdateRequest(
                rate.getTaxComponent().getId(),
                BigDecimal.ONE,
                rate.getEffectiveFrom(),
                null,
                false,
                false,
                0,
                TaxRateStatus.ACTIVE,
                null,
                null,
                null,
                null,
                rate.getVersion() + 1), null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Tax rate was modified");

        verify(taxRateRepository, never()).saveAndFlush(any());
        verify(auditService, never()).record(any());
    }

    private static TaxComponent component() {
        TaxType type = new TaxType("GST", "GST", null, true);
        Country country = new Country("CA", "Canada", true);
        TaxJurisdiction jurisdiction = new TaxJurisdiction(country, null, "GST", "GST", TaxJurisdictionType.NATIONAL, true);
        return new TaxComponent(type, jurisdiction, "GST", "GST", null, true);
    }
}
