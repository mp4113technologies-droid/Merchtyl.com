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
public class TaxComponentService {
    private final TaxComponentRepository taxComponentRepository;
    private final TaxTypeService taxTypeService;
    private final TaxJurisdictionRepository taxJurisdictionRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public TaxComponentService(
            TaxComponentRepository taxComponentRepository,
            TaxTypeService taxTypeService,
            TaxJurisdictionRepository taxJurisdictionRepository,
            UserRepository userRepository,
            AuditService auditService) {
        this.taxComponentRepository = taxComponentRepository;
        this.taxTypeService = taxTypeService;
        this.taxJurisdictionRepository = taxJurisdictionRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional
    public TaxComponentResponse create(TaxComponentRequest request, Authentication authentication) {
        String code = TaxGeographySupport.normalizeCode(request.code(), 64);
        if (taxComponentRepository.existsByCodeIgnoreCase(code)) {
            throw duplicate();
        }
        TaxComponentResponse response = TaxComponentResponse.from(save(new TaxComponent(
                taxTypeService.find(request.taxTypeId()),
                findJurisdiction(request.taxJurisdictionId()),
                code,
                TaxGeographySupport.cleanRequired(request.name(), "name"),
                TaxGeographySupport.optionalText(request.description()),
                request.active())));
        audit(authentication, AuditAction.TAX_COMPONENT_CREATED, response.id(), null, response);
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<TaxComponentResponse> search(TaxComponentSearchRequest request) {
        int pageNumber = Math.max(0, request.page());
        int pageSize = Math.max(1, Math.min(TaxGeographySupport.MAX_PAGE_SIZE, request.size()));
        var page = taxComponentRepository.findAll(
                specification(request),
                PageRequest.of(pageNumber, pageSize, Sort.by("name").and(Sort.by("id"))));
        return new PageResponse<>(
                page.getContent().stream().map(TaxComponentResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    @Transactional(readOnly = true)
    public TaxComponentResponse get(UUID id) {
        return TaxComponentResponse.from(find(id));
    }

    @Transactional
    public TaxComponentResponse update(UUID id, TaxComponentUpdateRequest request, Authentication authentication) {
        TaxComponent component = find(id);
        TaxGeographySupport.requireCurrentVersion(component.getVersion(), request.version(), "Tax component");
        String code = TaxGeographySupport.normalizeCode(request.code(), 64);
        if (taxComponentRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw duplicate();
        }
        TaxComponentResponse before = TaxComponentResponse.from(component);
        component.update(
                taxTypeService.find(request.taxTypeId()),
                findJurisdiction(request.taxJurisdictionId()),
                code,
                TaxGeographySupport.cleanRequired(request.name(), "name"),
                TaxGeographySupport.optionalText(request.description()),
                request.active());
        TaxComponentResponse after = TaxComponentResponse.from(save(component));
        audit(authentication, AuditAction.TAX_COMPONENT_UPDATED, id, before, after);
        return after;
    }

    @Transactional
    public TaxComponentResponse updateStatus(UUID id, TaxComponentStatusRequest request, Authentication authentication) {
        TaxComponent component = find(id);
        TaxGeographySupport.requireCurrentVersion(component.getVersion(), request.version(), "Tax component");
        TaxComponentResponse before = TaxComponentResponse.from(component);
        component.setActive(request.active());
        TaxComponentResponse after = TaxComponentResponse.from(save(component));
        audit(authentication, AuditAction.TAX_COMPONENT_STATUS_CHANGED, id, before, after);
        return after;
    }

    TaxComponent find(UUID id) {
        return taxComponentRepository.findById(id).orElseThrow(() -> new NotFoundException("Tax component not found"));
    }

    private TaxJurisdiction findJurisdiction(UUID id) {
        return taxJurisdictionRepository.findById(id).orElseThrow(() -> new NotFoundException("Tax jurisdiction not found"));
    }

    private TaxComponent save(TaxComponent component) {
        try {
            return taxComponentRepository.saveAndFlush(component);
        } catch (DataIntegrityViolationException exception) {
            throw duplicate();
        }
    }

    private Specification<TaxComponent> specification(TaxComponentSearchRequest request) {
        return Specification
                .where(TaxGeographySupport.<TaxComponent>equalReference("taxType", request.taxTypeId()))
                .and(TaxGeographySupport.equalReference("taxJurisdiction", request.taxJurisdictionId()))
                .and(TaxGeographySupport.equalString("code", TaxGeographySupport.normalizeCodeFilter(request.code())))
                .and(TaxGeographySupport.containsString("name", request.name()))
                .and(TaxGeographySupport.equalBoolean("active", request.active()));
    }

    private void audit(Authentication authentication, AuditAction action, UUID entityId, Object before, Object after) {
        TaxGeographySupport.audit(authentication, userRepository, auditService, action, "TAX_COMPONENT", entityId, before, after);
    }

    private ConflictException duplicate() {
        return new ConflictException("Tax component code already exists");
    }
}
