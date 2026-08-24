package com.merchtyl.tax;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
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
public class AdministrativeAreaService {
    private final AdministrativeAreaRepository administrativeAreaRepository;
    private final CountryService countryService;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public AdministrativeAreaService(
            AdministrativeAreaRepository administrativeAreaRepository,
            CountryService countryService,
            UserRepository userRepository,
            AuditService auditService) {
        this.administrativeAreaRepository = administrativeAreaRepository;
        this.countryService = countryService;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional
    public AdministrativeAreaResponse create(AdministrativeAreaRequest request, Authentication authentication) {
        Country country = countryService.find(request.countryId());
        String code = TaxGeographySupport.normalizeCode(request.code(), 16);
        if (administrativeAreaRepository.existsByCountryAndCodeIgnoreCase(country, code)) {
            throw duplicate();
        }
        AdministrativeAreaResponse response = AdministrativeAreaResponse.from(save(new AdministrativeArea(
                country,
                code,
                TaxGeographySupport.cleanRequired(request.name(), "name"),
                request.type(),
                request.active())));
        audit(authentication, AuditAction.ADMINISTRATIVE_AREA_CREATED, response.id(), null, response);
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdministrativeAreaResponse> search(AdministrativeAreaSearchRequest request) {
        int pageNumber = Math.max(0, request.page());
        int pageSize = Math.max(1, Math.min(TaxGeographySupport.MAX_PAGE_SIZE, request.size()));
        var page = administrativeAreaRepository.findAll(
                specification(request),
                PageRequest.of(pageNumber, pageSize,
                        Sort.by("country.code").and(Sort.by("name")).and(Sort.by("id"))));
        return new PageResponse<>(
                page.getContent().stream().map(AdministrativeAreaResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    @Transactional(readOnly = true)
    public AdministrativeAreaResponse get(UUID id) {
        return AdministrativeAreaResponse.from(find(id));
    }

    @Transactional
    public AdministrativeAreaResponse update(UUID id, AdministrativeAreaUpdateRequest request, Authentication authentication) {
        AdministrativeArea area = find(id);
        TaxGeographySupport.requireCurrentVersion(area.getVersion(), request.version(), "Administrative area");
        Country country = countryService.find(request.countryId());
        String code = TaxGeographySupport.normalizeCode(request.code(), 16);
        if (administrativeAreaRepository.existsByCountryAndCodeIgnoreCaseAndIdNot(country, code, id)) {
            throw duplicate();
        }
        AdministrativeAreaResponse before = AdministrativeAreaResponse.from(area);
        area.update(country, code, TaxGeographySupport.cleanRequired(request.name(), "name"), request.type(), request.active());
        AdministrativeAreaResponse after = AdministrativeAreaResponse.from(save(area));
        audit(authentication, AuditAction.ADMINISTRATIVE_AREA_UPDATED, id, before, after);
        return after;
    }

    @Transactional
    public AdministrativeAreaResponse updateStatus(UUID id, AdministrativeAreaStatusRequest request, Authentication authentication) {
        AdministrativeArea area = find(id);
        TaxGeographySupport.requireCurrentVersion(area.getVersion(), request.version(), "Administrative area");
        AdministrativeAreaResponse before = AdministrativeAreaResponse.from(area);
        area.setActive(request.active());
        AdministrativeAreaResponse after = AdministrativeAreaResponse.from(save(area));
        audit(authentication, AuditAction.ADMINISTRATIVE_AREA_STATUS_CHANGED, id, before, after);
        return after;
    }

    AdministrativeArea find(UUID id) {
        return administrativeAreaRepository.findById(id).orElseThrow(() -> new NotFoundException("Administrative area not found"));
    }

    private AdministrativeArea save(AdministrativeArea area) {
        try {
            return administrativeAreaRepository.saveAndFlush(area);
        } catch (DataIntegrityViolationException exception) {
            throw duplicate();
        }
    }

    private Specification<AdministrativeArea> specification(AdministrativeAreaSearchRequest request) {
        return Specification
                .where(TaxGeographySupport.<AdministrativeArea>equalReference("country", request.countryId()))
                .and(TaxGeographySupport.equalString("code", TaxGeographySupport.normalizeCodeFilter(request.code())))
                .and(TaxGeographySupport.containsString("name", request.name()))
                .and(TaxGeographySupport.equalEnum("type", request.type()))
                .and(TaxGeographySupport.equalBoolean("active", request.active()));
    }

    private void audit(Authentication authentication, AuditAction action, UUID entityId, Object before, Object after) {
        TaxGeographySupport.audit(authentication, userRepository, auditService, action, "ADMINISTRATIVE_AREA", entityId, before, after);
    }

    private ConflictException duplicate() {
        return new ConflictException("Administrative area code already exists for country");
    }
}
