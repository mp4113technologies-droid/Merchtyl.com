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
public class CountryService {
    private final CountryRepository countryRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public CountryService(CountryRepository countryRepository, UserRepository userRepository, AuditService auditService) {
        this.countryRepository = countryRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional
    public CountryResponse create(CountryRequest request, Authentication authentication) {
        String code = TaxGeographySupport.normalizeCountryCode(request.code());
        if (countryRepository.existsByCodeIgnoreCase(code)) {
            throw duplicate();
        }
        CountryResponse response = CountryResponse.from(save(new Country(code, TaxGeographySupport.cleanRequired(request.name(), "name"), request.active())));
        audit(authentication, AuditAction.COUNTRY_CREATED, response.id(), null, response);
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<CountryResponse> search(CountrySearchRequest request) {
        int pageNumber = Math.max(0, request.page());
        int pageSize = Math.max(1, Math.min(TaxGeographySupport.MAX_PAGE_SIZE, request.size()));
        var page = countryRepository.findAll(
                specification(request),
                PageRequest.of(pageNumber, pageSize, Sort.by("name").and(Sort.by("id"))));
        return new PageResponse<>(page.getContent().stream().map(CountryResponse::from).toList(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
    }

    @Transactional(readOnly = true)
    public CountryResponse get(UUID id) {
        return CountryResponse.from(find(id));
    }

    @Transactional
    public CountryResponse update(UUID id, CountryUpdateRequest request, Authentication authentication) {
        Country country = find(id);
        TaxGeographySupport.requireCurrentVersion(country.getVersion(), request.version(), "Country");
        String code = TaxGeographySupport.normalizeCountryCode(request.code());
        if (countryRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw duplicate();
        }
        CountryResponse before = CountryResponse.from(country);
        country.update(code, TaxGeographySupport.cleanRequired(request.name(), "name"), request.active());
        CountryResponse after = CountryResponse.from(save(country));
        audit(authentication, AuditAction.COUNTRY_UPDATED, id, before, after);
        return after;
    }

    @Transactional
    public CountryResponse updateStatus(UUID id, CountryStatusRequest request, Authentication authentication) {
        Country country = find(id);
        TaxGeographySupport.requireCurrentVersion(country.getVersion(), request.version(), "Country");
        CountryResponse before = CountryResponse.from(country);
        country.setActive(request.active());
        CountryResponse after = CountryResponse.from(save(country));
        audit(authentication, AuditAction.COUNTRY_STATUS_CHANGED, id, before, after);
        return after;
    }

    Country find(UUID id) {
        return countryRepository.findById(id).orElseThrow(() -> new NotFoundException("Country not found"));
    }

    private Country save(Country country) {
        try {
            return countryRepository.saveAndFlush(country);
        } catch (DataIntegrityViolationException exception) {
            throw duplicate();
        }
    }

    private Specification<Country> specification(CountrySearchRequest request) {
        return Specification
                .where(TaxGeographySupport.<Country>equalString("code", TaxGeographySupport.normalizeCodeFilter(request.code())))
                .and(TaxGeographySupport.containsString("name", request.name()))
                .and(TaxGeographySupport.equalBoolean("active", request.active()));
    }

    private void audit(Authentication authentication, AuditAction action, UUID entityId, Object before, Object after) {
        TaxGeographySupport.audit(authentication, userRepository, auditService, action, "COUNTRY", entityId, before, after);
    }

    private ConflictException duplicate() {
        return new ConflictException("Country code already exists");
    }
}
