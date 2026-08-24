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
public class TaxTypeService {
    private final TaxTypeRepository taxTypeRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public TaxTypeService(TaxTypeRepository taxTypeRepository, UserRepository userRepository, AuditService auditService) {
        this.taxTypeRepository = taxTypeRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional
    public TaxTypeResponse create(TaxTypeRequest request, Authentication authentication) {
        String code = TaxGeographySupport.normalizeCode(request.code(), 32);
        if (taxTypeRepository.existsByCodeIgnoreCase(code)) {
            throw duplicate();
        }
        TaxTypeResponse response = TaxTypeResponse.from(save(new TaxType(
                code,
                TaxGeographySupport.cleanRequired(request.name(), "name"),
                TaxGeographySupport.optionalText(request.description()),
                request.active())));
        audit(authentication, AuditAction.TAX_TYPE_CREATED, response.id(), null, response);
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<TaxTypeResponse> search(TaxTypeSearchRequest request) {
        int pageNumber = Math.max(0, request.page());
        int pageSize = Math.max(1, Math.min(TaxGeographySupport.MAX_PAGE_SIZE, request.size()));
        var page = taxTypeRepository.findAll(
                specification(request),
                PageRequest.of(pageNumber, pageSize, Sort.by("name").and(Sort.by("id"))));
        return new PageResponse<>(
                page.getContent().stream().map(TaxTypeResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    @Transactional(readOnly = true)
    public TaxTypeResponse get(UUID id) {
        return TaxTypeResponse.from(find(id));
    }

    @Transactional
    public TaxTypeResponse update(UUID id, TaxTypeUpdateRequest request, Authentication authentication) {
        TaxType taxType = find(id);
        TaxGeographySupport.requireCurrentVersion(taxType.getVersion(), request.version(), "Tax type");
        String code = TaxGeographySupport.normalizeCode(request.code(), 32);
        if (taxTypeRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw duplicate();
        }
        TaxTypeResponse before = TaxTypeResponse.from(taxType);
        taxType.update(
                code,
                TaxGeographySupport.cleanRequired(request.name(), "name"),
                TaxGeographySupport.optionalText(request.description()),
                request.active());
        TaxTypeResponse after = TaxTypeResponse.from(save(taxType));
        audit(authentication, AuditAction.TAX_TYPE_UPDATED, id, before, after);
        return after;
    }

    @Transactional
    public TaxTypeResponse updateStatus(UUID id, TaxTypeStatusRequest request, Authentication authentication) {
        TaxType taxType = find(id);
        TaxGeographySupport.requireCurrentVersion(taxType.getVersion(), request.version(), "Tax type");
        TaxTypeResponse before = TaxTypeResponse.from(taxType);
        taxType.setActive(request.active());
        TaxTypeResponse after = TaxTypeResponse.from(save(taxType));
        audit(authentication, AuditAction.TAX_TYPE_STATUS_CHANGED, id, before, after);
        return after;
    }

    TaxType find(UUID id) {
        return taxTypeRepository.findById(id).orElseThrow(() -> new NotFoundException("Tax type not found"));
    }

    private TaxType save(TaxType taxType) {
        try {
            return taxTypeRepository.saveAndFlush(taxType);
        } catch (DataIntegrityViolationException exception) {
            throw duplicate();
        }
    }

    private Specification<TaxType> specification(TaxTypeSearchRequest request) {
        return Specification
                .where(TaxGeographySupport.<TaxType>equalString("code", TaxGeographySupport.normalizeCodeFilter(request.code())))
                .and(TaxGeographySupport.containsString("name", request.name()))
                .and(TaxGeographySupport.equalBoolean("active", request.active()));
    }

    private void audit(Authentication authentication, AuditAction action, UUID entityId, Object before, Object after) {
        TaxGeographySupport.audit(authentication, userRepository, auditService, action, "TAX_TYPE", entityId, before, after);
    }

    private ConflictException duplicate() {
        return new ConflictException("Tax type code already exists");
    }
}
