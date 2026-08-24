package com.merchtyl.catalogue;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.security.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class UnitOfMeasureService extends CatalogueReferenceService<UnitOfMeasure> {
    public UnitOfMeasureService(UnitOfMeasureRepository repository, UserRepository userRepository, AuditService auditService) {
        super(
                repository,
                repository,
                repository,
                userRepository,
                auditService,
                values -> new UnitOfMeasure(values.code(), values.name(), values.description(), values.active()),
                new CatalogueReferenceAuditActions(
                        AuditAction.UNIT_CREATED,
                        AuditAction.UNIT_UPDATED,
                        AuditAction.UNIT_STATUS_CHANGED),
                "UNIT_OF_MEASURE",
                "Unit of measure");
    }
}
