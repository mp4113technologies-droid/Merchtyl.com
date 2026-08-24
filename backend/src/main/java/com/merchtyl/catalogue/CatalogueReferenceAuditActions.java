package com.merchtyl.catalogue;

import com.merchtyl.audit.AuditAction;

record CatalogueReferenceAuditActions(
        AuditAction created,
        AuditAction updated,
        AuditAction statusChanged
) {
}
