package com.merchtyl.tax;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.ConflictException;
import com.merchtyl.common.NotFoundException;
import com.merchtyl.common.PageResponse;
import com.merchtyl.security.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TaxJurisdictionService {
    private final TaxJurisdictionRepository taxJurisdictionRepository;
    private final CountryService countryService;
    private final AdministrativeAreaService administrativeAreaService;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public TaxJurisdictionService(
            TaxJurisdictionRepository taxJurisdictionRepository,
            CountryService countryService,
            AdministrativeAreaService administrativeAreaService,
            UserRepository userRepository,
            AuditService auditService) {
        this.taxJurisdictionRepository = taxJurisdictionRepository;
        this.countryService = countryService;
        this.administrativeAreaService = administrativeAreaService;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional
    public TaxJurisdictionResponse create(TaxJurisdictionRequest request, Authentication authentication) {
        TaxJurisdictionValues values = values(request.countryId(), request.administrativeAreaId(), request.code(), request.name(), request.type(), request.active());
        if (taxJurisdictionRepository.existsByCountryAndCodeIgnoreCase(values.country(), values.code())) {
            throw duplicate();
        }
        TaxJurisdictionResponse response = TaxJurisdictionResponse.from(save(new TaxJurisdiction(
                values.country(),
                values.administrativeArea(),
                values.code(),
                values.name(),
                values.type(),
                values.active())));
        audit(authentication, AuditAction.TAX_JURISDICTION_CREATED, response.id(), null, response);
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<TaxJurisdictionResponse> search(TaxJurisdictionSearchRequest request) {
        int pageNumber = Math.max(0, request.page());
        int pageSize = Math.max(1, Math.min(TaxGeographySupport.MAX_PAGE_SIZE, request.size()));
        var page = taxJurisdictionRepository.findAll(
                specification(request),
                PageRequest.of(pageNumber, pageSize,
                        Sort.by("country.code").and(Sort.by("name")).and(Sort.by("id"))));
        return new PageResponse<>(
                page.getContent().stream().map(TaxJurisdictionResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    @Transactional(readOnly = true)
    public TaxJurisdictionResponse get(UUID id) {
        return TaxJurisdictionResponse.from(find(id));
    }

    @Transactional
    public TaxJurisdictionResponse update(UUID id, TaxJurisdictionUpdateRequest request, Authentication authentication) {
        TaxJurisdiction jurisdiction = find(id);
        TaxGeographySupport.requireCurrentVersion(jurisdiction.getVersion(), request.version(), "Tax jurisdiction");
        TaxJurisdictionValues values = values(request.countryId(), request.administrativeAreaId(), request.code(), request.name(), request.type(), request.active());
        if (taxJurisdictionRepository.existsByCountryAndCodeIgnoreCaseAndIdNot(values.country(), values.code(), id)) {
            throw duplicate();
        }
        TaxJurisdictionResponse before = TaxJurisdictionResponse.from(jurisdiction);
        jurisdiction.update(values.country(), values.administrativeArea(), values.code(), values.name(), values.type(), values.active());
        TaxJurisdictionResponse after = TaxJurisdictionResponse.from(save(jurisdiction));
        audit(authentication, AuditAction.TAX_JURISDICTION_UPDATED, id, before, after);
        return after;
    }

    @Transactional
    public TaxJurisdictionResponse updateStatus(UUID id, TaxJurisdictionStatusRequest request, Authentication authentication) {
        TaxJurisdiction jurisdiction = find(id);
        TaxGeographySupport.requireCurrentVersion(jurisdiction.getVersion(), request.version(), "Tax jurisdiction");
        TaxJurisdictionResponse before = TaxJurisdictionResponse.from(jurisdiction);
        jurisdiction.setActive(request.active());
        TaxJurisdictionResponse after = TaxJurisdictionResponse.from(save(jurisdiction));
        audit(authentication, AuditAction.TAX_JURISDICTION_STATUS_CHANGED, id, before, after);
        return after;
    }

    private TaxJurisdictionValues values(UUID countryId, UUID administrativeAreaId, String code, String name, TaxJurisdictionType type, boolean active) {
        Country country = countryService.find(countryId);
        AdministrativeArea administrativeArea = administrativeAreaId == null ? null : administrativeAreaService.find(administrativeAreaId);
        if (type == TaxJurisdictionType.NATIONAL && administrativeArea != null) {
            throw new BadRequestException("National jurisdictions cannot be tied to an administrative area");
        }
        if (administrativeArea != null && !administrativeArea.getCountry().getId().equals(country.getId())) {
            throw new BadRequestException("Administrative area must belong to the selected country");
        }
        return new TaxJurisdictionValues(
                country,
                administrativeArea,
                TaxGeographySupport.normalizeCode(code, 64),
                TaxGeographySupport.cleanRequired(name, "name"),
                type,
                active);
    }

    private TaxJurisdiction find(UUID id) {
        return taxJurisdictionRepository.findById(id).orElseThrow(() -> new NotFoundException("Tax jurisdiction not found"));
    }

    private TaxJurisdiction save(TaxJurisdiction jurisdiction) {
        try {
            return taxJurisdictionRepository.saveAndFlush(jurisdiction);
        } catch (DataIntegrityViolationException exception) {
            throw duplicate();
        }
    }

    private Specification<TaxJurisdiction> specification(TaxJurisdictionSearchRequest request) {
        return Specification
                .where(TaxGeographySupport.<TaxJurisdiction>equalReference("country", request.countryId()))
                .and(TaxGeographySupport.equalReference("administrativeArea", request.administrativeAreaId()))
                .and(TaxGeographySupport.equalString("code", TaxGeographySupport.normalizeCodeFilter(request.code())))
                .and(TaxGeographySupport.containsString("name", request.name()))
                .and(TaxGeographySupport.equalEnum("type", request.type()))
                .and(TaxGeographySupport.equalBoolean("active", request.active()));
    }

    private void audit(Authentication authentication, AuditAction action, UUID entityId, Object before, Object after) {
        TaxGeographySupport.audit(authentication, userRepository, auditService, action, "TAX_JURISDICTION", entityId, before, after);
    }

    private ConflictException duplicate() {
        return new ConflictException("Tax jurisdiction code already exists for country");
    }

    private record TaxJurisdictionValues(
            Country country,
            AdministrativeArea administrativeArea,
            String code,
            String name,
            TaxJurisdictionType type,
            boolean active) {
    }
}
