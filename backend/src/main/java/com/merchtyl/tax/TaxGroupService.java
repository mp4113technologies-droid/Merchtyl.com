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
public class TaxGroupService {
    private final TaxGroupRepository taxGroupRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public TaxGroupService(TaxGroupRepository taxGroupRepository, UserRepository userRepository, AuditService auditService) {
        this.taxGroupRepository = taxGroupRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional
    public TaxGroupResponse create(TaxGroupRequest request, Authentication authentication) {
        String code = TaxGeographySupport.normalizeCode(request.code(), 64);
        if (taxGroupRepository.existsByCodeIgnoreCase(code)) {
            throw duplicate();
        }
        TaxGroupResponse response = TaxGroupResponse.from(save(new TaxGroup(
                code,
                TaxGeographySupport.cleanRequired(request.name(), "name"),
                TaxGeographySupport.optionalText(request.description()),
                request.active())));
        audit(authentication, AuditAction.TAX_GROUP_CREATED, response.id(), null, response);
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<TaxGroupResponse> search(TaxGroupSearchRequest request) {
        int pageNumber = Math.max(0, request.page());
        int pageSize = Math.max(1, Math.min(TaxGeographySupport.MAX_PAGE_SIZE, request.size()));
        var page = taxGroupRepository.findAll(
                specification(request),
                PageRequest.of(pageNumber, pageSize, Sort.by("name").and(Sort.by("id"))));
        return new PageResponse<>(page.getContent().stream().map(TaxGroupResponse::from).toList(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
    }

    @Transactional(readOnly = true)
    public TaxGroupResponse get(UUID id) {
        return TaxGroupResponse.from(find(id));
    }

    @Transactional
    public TaxGroupResponse update(UUID id, TaxGroupUpdateRequest request, Authentication authentication) {
        TaxGroup group = find(id);
        TaxGeographySupport.requireCurrentVersion(group.getVersion(), request.version(), "Tax group");
        String code = TaxGeographySupport.normalizeCode(request.code(), 64);
        if (taxGroupRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw duplicate();
        }
        TaxGroupResponse before = TaxGroupResponse.from(group);
        group.update(code, TaxGeographySupport.cleanRequired(request.name(), "name"), TaxGeographySupport.optionalText(request.description()), request.active());
        TaxGroupResponse after = TaxGroupResponse.from(save(group));
        audit(authentication, AuditAction.TAX_GROUP_UPDATED, id, before, after);
        return after;
    }

    @Transactional
    public TaxGroupResponse updateStatus(UUID id, TaxGroupStatusRequest request, Authentication authentication) {
        TaxGroup group = find(id);
        TaxGeographySupport.requireCurrentVersion(group.getVersion(), request.version(), "Tax group");
        TaxGroupResponse before = TaxGroupResponse.from(group);
        group.setActive(request.active());
        TaxGroupResponse after = TaxGroupResponse.from(save(group));
        audit(authentication, AuditAction.TAX_GROUP_STATUS_CHANGED, id, before, after);
        return after;
    }

    TaxGroup find(UUID id) {
        return taxGroupRepository.findById(id).orElseThrow(() -> new NotFoundException("Tax group not found"));
    }

    private TaxGroup save(TaxGroup group) {
        try {
            return taxGroupRepository.saveAndFlush(group);
        } catch (DataIntegrityViolationException exception) {
            throw duplicate();
        }
    }

    private Specification<TaxGroup> specification(TaxGroupSearchRequest request) {
        return Specification
                .where(TaxGeographySupport.<TaxGroup>equalString("code", TaxGeographySupport.normalizeCodeFilter(request.code())))
                .and(TaxGeographySupport.containsString("name", request.name()))
                .and(TaxGeographySupport.equalBoolean("active", request.active()));
    }

    private void audit(Authentication authentication, AuditAction action, UUID entityId, Object before, Object after) {
        TaxGeographySupport.audit(authentication, userRepository, auditService, action, "TAX_GROUP", entityId, before, after);
    }

    private ConflictException duplicate() {
        return new ConflictException("Tax group code already exists");
    }
}
