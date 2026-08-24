package com.merchtyl.tax;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.ConflictException;
import com.merchtyl.common.NotFoundException;
import com.merchtyl.common.PageResponse;
import com.merchtyl.security.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@Service
public class TaxRateService {
    private static final LocalDate OPEN_ENDED_EFFECTIVE_TO = LocalDate.of(9999, 12, 31);
    private static final Set<TaxRateStatus> OVERLAP_BLOCKING_STATUSES = Set.of(TaxRateStatus.ACTIVE, TaxRateStatus.SCHEDULED);

    private final TaxRateRepository taxRateRepository;
    private final TaxComponentService taxComponentService;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public TaxRateService(
            TaxRateRepository taxRateRepository,
            TaxComponentService taxComponentService,
            UserRepository userRepository,
            AuditService auditService) {
        this.taxRateRepository = taxRateRepository;
        this.taxComponentService = taxComponentService;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional
    public TaxRateResponse create(TaxRateRequest request, Authentication authentication) {
        TaxRateValues values = values(
                request.taxComponentId(),
                request.percentageRate(),
                request.effectiveFrom(),
                request.effectiveTo(),
                request.includedInPrice(),
                request.compoundOnPreviousTax(),
                request.calculationOrder(),
                request.status(),
                request.source(),
                request.sourceReference(),
                request.verifiedBy(),
                request.verifiedAt());
        requireNoOverlap(values, null);
        TaxRateResponse response = TaxRateResponse.from(taxRateRepository.saveAndFlush(new TaxRate(values)));
        audit(authentication, AuditAction.TAX_RATE_CREATED, response.id(), null, response);
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<TaxRateResponse> search(TaxRateSearchRequest request) {
        int pageNumber = Math.max(0, request.page());
        int pageSize = Math.max(1, Math.min(TaxGeographySupport.MAX_PAGE_SIZE, request.size()));
        var page = taxRateRepository.findAll(
                specification(request),
                PageRequest.of(pageNumber, pageSize,
                        Sort.by("taxComponent.code")
                                .and(Sort.by(Sort.Direction.DESC, "effectiveFrom"))
                                .and(Sort.by(Sort.Direction.DESC, "id"))));
        return new PageResponse<>(
                page.getContent().stream().map(TaxRateResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    @Transactional(readOnly = true)
    public TaxRateResponse get(UUID id) {
        return TaxRateResponse.from(find(id));
    }

    @Transactional
    public TaxRateResponse update(UUID id, TaxRateUpdateRequest request, Authentication authentication) {
        TaxRate rate = find(id);
        TaxGeographySupport.requireCurrentVersion(rate.getVersion(), request.version(), "Tax rate");
        TaxRateValues values = values(
                request.taxComponentId(),
                request.percentageRate(),
                request.effectiveFrom(),
                request.effectiveTo(),
                request.includedInPrice(),
                request.compoundOnPreviousTax(),
                request.calculationOrder(),
                request.status(),
                request.source(),
                request.sourceReference(),
                request.verifiedBy(),
                request.verifiedAt());
        requireNoOverlap(values, id);
        TaxRateResponse before = TaxRateResponse.from(rate);
        rate.update(values);
        TaxRateResponse after = TaxRateResponse.from(taxRateRepository.saveAndFlush(rate));
        audit(authentication, AuditAction.TAX_RATE_UPDATED, id, before, after);
        return after;
    }

    @Transactional
    public TaxRateResponse updateStatus(UUID id, TaxRateStatusRequest request, Authentication authentication) {
        TaxRate rate = find(id);
        TaxGeographySupport.requireCurrentVersion(rate.getVersion(), request.version(), "Tax rate");
        if (OVERLAP_BLOCKING_STATUSES.contains(request.status())) {
            requireNoOverlap(new TaxRateValues(
                    rate.getTaxComponent(),
                    rate.getPercentageRate(),
                    rate.getEffectiveFrom(),
                    rate.getEffectiveTo(),
                    rate.isIncludedInPrice(),
                    rate.isCompoundOnPreviousTax(),
                    rate.getCalculationOrder(),
                    request.status(),
                    rate.getSource(),
                    rate.getSourceReference(),
                    rate.getVerifiedBy(),
                    rate.getVerifiedAt()), id);
        }
        TaxRateResponse before = TaxRateResponse.from(rate);
        rate.setStatus(request.status());
        TaxRateResponse after = TaxRateResponse.from(taxRateRepository.saveAndFlush(rate));
        audit(authentication, AuditAction.TAX_RATE_STATUS_CHANGED, id, before, after);
        return after;
    }

    private TaxRateValues values(
            UUID taxComponentId,
            BigDecimal percentageRate,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            boolean includedInPrice,
            boolean compoundOnPreviousTax,
            int calculationOrder,
            TaxRateStatus status,
            String source,
            String sourceReference,
            String verifiedBy,
            java.time.Instant verifiedAt) {
        if (percentageRate == null || percentageRate.signum() < 0) {
            throw new BadRequestException("percentageRate must be zero or greater");
        }
        if (effectiveFrom == null) {
            throw new BadRequestException("effectiveFrom is required");
        }
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new BadRequestException("effectiveTo must be on or after effectiveFrom");
        }
        if (calculationOrder < 0) {
            throw new BadRequestException("calculationOrder must be zero or greater");
        }
        if (status == null) {
            throw new BadRequestException("status is required");
        }
        return new TaxRateValues(
                taxComponentService.find(taxComponentId),
                percentageRate,
                effectiveFrom,
                effectiveTo,
                includedInPrice,
                compoundOnPreviousTax,
                calculationOrder,
                status,
                TaxGeographySupport.optionalText(source),
                TaxGeographySupport.optionalText(sourceReference),
                TaxGeographySupport.optionalText(verifiedBy),
                verifiedAt);
    }

    private void requireNoOverlap(TaxRateValues values, UUID excludeId) {
        if (!OVERLAP_BLOCKING_STATUSES.contains(values.status())) {
            return;
        }
        LocalDate effectiveTo = values.effectiveTo() == null ? OPEN_ENDED_EFFECTIVE_TO : values.effectiveTo();
        if (taxRateRepository.existsOverlappingActivePeriod(
                values.taxComponent().getId(),
                values.effectiveFrom(),
                effectiveTo,
                OVERLAP_BLOCKING_STATUSES,
                excludeId)) {
            throw new ConflictException("Tax rate effective period overlaps an active or scheduled rate for this component");
        }
    }

    private TaxRate find(UUID id) {
        return taxRateRepository.findById(id).orElseThrow(() -> new NotFoundException("Tax rate not found"));
    }

    private Specification<TaxRate> specification(TaxRateSearchRequest request) {
        return Specification
                .where(TaxGeographySupport.<TaxRate>equalReference("taxComponent", request.taxComponentId()))
                .and(TaxGeographySupport.equalEnum("status", request.status()))
                .and(TaxGeographySupport.equalBoolean("includedInPrice", request.includedInPrice()))
                .and(TaxGeographySupport.equalBoolean("compoundOnPreviousTax", request.compoundOnPreviousTax()))
                .and(TaxGeographySupport.equalInteger("calculationOrder", request.calculationOrder()));
    }

    private void audit(Authentication authentication, AuditAction action, UUID entityId, Object before, Object after) {
        TaxGeographySupport.audit(authentication, userRepository, auditService, action, "TAX_RATE", entityId, before, after);
    }
}
