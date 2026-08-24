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
public class TaxGroupComponentService {
    private final TaxGroupComponentRepository taxGroupComponentRepository;
    private final TaxGroupService taxGroupService;
    private final TaxComponentService taxComponentService;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public TaxGroupComponentService(
            TaxGroupComponentRepository taxGroupComponentRepository,
            TaxGroupService taxGroupService,
            TaxComponentService taxComponentService,
            UserRepository userRepository,
            AuditService auditService) {
        this.taxGroupComponentRepository = taxGroupComponentRepository;
        this.taxGroupService = taxGroupService;
        this.taxComponentService = taxComponentService;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional
    public TaxGroupComponentResponse create(TaxGroupComponentRequest request, Authentication authentication) {
        TaxGroup group = taxGroupService.find(request.taxGroupId());
        TaxComponent component = taxComponentService.find(request.taxComponentId());
        validateCalculationOrder(request.calculationOrder());
        if (taxGroupComponentRepository.existsByTaxGroupAndTaxComponent(group, component)) {
            throw duplicate();
        }
        TaxGroupComponentResponse response = TaxGroupComponentResponse.from(save(new TaxGroupComponent(group, component, request.calculationOrder(), request.active())));
        audit(authentication, AuditAction.TAX_GROUP_COMPONENT_CREATED, response.id(), null, response);
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<TaxGroupComponentResponse> search(TaxGroupComponentSearchRequest request) {
        int pageNumber = Math.max(0, request.page());
        int pageSize = Math.max(1, Math.min(TaxGeographySupport.MAX_PAGE_SIZE, request.size()));
        var page = taxGroupComponentRepository.findAll(
                specification(request),
                PageRequest.of(pageNumber, pageSize, Sort.by("calculationOrder").and(Sort.by("id"))));
        return new PageResponse<>(page.getContent().stream().map(TaxGroupComponentResponse::from).toList(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
    }

    @Transactional(readOnly = true)
    public TaxGroupComponentResponse get(UUID id) {
        return TaxGroupComponentResponse.from(find(id));
    }

    @Transactional
    public TaxGroupComponentResponse update(UUID id, TaxGroupComponentUpdateRequest request, Authentication authentication) {
        TaxGroupComponent groupComponent = find(id);
        TaxGeographySupport.requireCurrentVersion(groupComponent.getVersion(), request.version(), "Tax group component");
        TaxGroup group = taxGroupService.find(request.taxGroupId());
        TaxComponent component = taxComponentService.find(request.taxComponentId());
        validateCalculationOrder(request.calculationOrder());
        if (taxGroupComponentRepository.existsByTaxGroupAndTaxComponentAndIdNot(group, component, id)) {
            throw duplicate();
        }
        TaxGroupComponentResponse before = TaxGroupComponentResponse.from(groupComponent);
        groupComponent.update(group, component, request.calculationOrder(), request.active());
        TaxGroupComponentResponse after = TaxGroupComponentResponse.from(save(groupComponent));
        audit(authentication, AuditAction.TAX_GROUP_COMPONENT_UPDATED, id, before, after);
        return after;
    }

    @Transactional
    public TaxGroupComponentResponse updateStatus(UUID id, TaxGroupComponentStatusRequest request, Authentication authentication) {
        TaxGroupComponent groupComponent = find(id);
        TaxGeographySupport.requireCurrentVersion(groupComponent.getVersion(), request.version(), "Tax group component");
        TaxGroupComponentResponse before = TaxGroupComponentResponse.from(groupComponent);
        groupComponent.setActive(request.active());
        TaxGroupComponentResponse after = TaxGroupComponentResponse.from(save(groupComponent));
        audit(authentication, AuditAction.TAX_GROUP_COMPONENT_STATUS_CHANGED, id, before, after);
        return after;
    }

    private TaxGroupComponent find(UUID id) {
        return taxGroupComponentRepository.findById(id).orElseThrow(() -> new NotFoundException("Tax group component not found"));
    }

    private TaxGroupComponent save(TaxGroupComponent groupComponent) {
        try {
            return taxGroupComponentRepository.saveAndFlush(groupComponent);
        } catch (DataIntegrityViolationException exception) {
            throw duplicate();
        }
    }

    private void validateCalculationOrder(int calculationOrder) {
        if (calculationOrder < 0) {
            throw new BadRequestException("calculationOrder must be zero or greater");
        }
    }

    private Specification<TaxGroupComponent> specification(TaxGroupComponentSearchRequest request) {
        return Specification
                .where(TaxGeographySupport.<TaxGroupComponent>equalReference("taxGroup", request.taxGroupId()))
                .and(TaxGeographySupport.equalReference("taxComponent", request.taxComponentId()))
                .and(TaxGeographySupport.equalBoolean("active", request.active()));
    }

    private void audit(Authentication authentication, AuditAction action, UUID entityId, Object before, Object after) {
        TaxGeographySupport.audit(authentication, userRepository, auditService, action, "TAX_GROUP_COMPONENT", entityId, before, after);
    }

    private ConflictException duplicate() {
        return new ConflictException("Tax component is already assigned to this tax group");
    }
}
