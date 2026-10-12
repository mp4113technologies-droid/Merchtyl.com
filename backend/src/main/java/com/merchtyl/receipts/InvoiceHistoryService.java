package com.merchtyl.receipts;

import com.merchtyl.common.PageResponse;
import com.merchtyl.security.StoreAccessService;
import com.merchtyl.security.User;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class InvoiceHistoryService {
    private static final int MAX_PAGE_SIZE = 100;

    private final InvoiceHistoryQueryRepository repository;
    private final StoreAccessService storeAccessService;

    public InvoiceHistoryService(
            InvoiceHistoryQueryRepository repository,
            StoreAccessService storeAccessService) {
        this.repository = repository;
        this.storeAccessService = storeAccessService;
    }

    @Transactional(readOnly = true)
    public PageResponse<InvoiceHistorySummary> search(
            UUID storeId,
            String invoiceNumber,
            int page,
            int size,
            Authentication authentication) {
        User actor = storeAccessService.currentTenantUser(authentication);
        boolean owner = StoreAccessService.isOwner(storeAccessService.roles(actor));
        Set<UUID> authorizedStoreIds = owner
                ? Set.of()
                : storeAccessService.getActiveAssignedStoreIds(actor.getId());
        if (storeId != null) {
            storeAccessService.requireStoreAccess(authentication, storeId);
        }

        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(MAX_PAGE_SIZE, size));
        String search = invoiceNumber == null ? "" : invoiceNumber.trim();
        if (!owner && authorizedStoreIds.isEmpty()) {
            return new PageResponse<>(List.of(), safePage, safeSize, 0, 0, safePage == 0, true);
        }

        long total = repository.count(
                actor.getTenantId(), authorizedStoreIds, owner, storeId, search);
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / safeSize);
        List<InvoiceHistorySummary> content = total == 0
                ? List.of()
                : repository.find(actor.getTenantId(), authorizedStoreIds, owner, storeId,
                        search, safePage, safeSize);
        return new PageResponse<>(
                content,
                safePage,
                safeSize,
                total,
                totalPages,
                safePage == 0,
                totalPages == 0 || safePage >= totalPages - 1);
    }
}
