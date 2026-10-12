package com.merchtyl.receipts;

import com.merchtyl.register.RegisterType;
import com.merchtyl.sales.SaleStatus;
import com.merchtyl.security.RoleName;
import com.merchtyl.security.StoreAccessService;
import com.merchtyl.security.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceHistoryServiceTest {
    @Mock InvoiceHistoryQueryRepository repository;
    @Mock StoreAccessService storeAccessService;
    @Mock Authentication authentication;
    @Mock User actor;

    private InvoiceHistoryService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID storeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new InvoiceHistoryService(repository, storeAccessService);
        when(storeAccessService.currentTenantUser(authentication)).thenReturn(actor);
        when(actor.getTenantId()).thenReturn(tenantId);
    }

    @Test
    void returnsRequestedTenRecordPageNewestFirstFromRepository() {
        Set<UUID> stores = Set.of(storeId);
        when(actor.getId()).thenReturn(userId);
        when(storeAccessService.roles(actor)).thenReturn(Set.of(RoleName.MANAGER));
        when(storeAccessService.getActiveAssignedStoreIds(userId)).thenReturn(stores);
        when(repository.count(tenantId, stores, false, null, "")).thenReturn(25L);
        InvoiceHistorySummary row = summary("1025");
        when(repository.find(tenantId, stores, false, null, "", 1, 10)).thenReturn(List.of(row));

        var result = service.search(null, null, 1, 10, authentication);

        assertThat(result.content()).containsExactly(row);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(10);
        assertThat(result.totalElements()).isEqualTo(25);
        assertThat(result.totalPages()).isEqualTo(3);
        assertThat(result.first()).isFalse();
        assertThat(result.last()).isFalse();
    }

    @Test
    void trimsSearchAndRequiresAccessToExplicitStore() {
        Set<UUID> stores = Set.of(storeId);
        when(actor.getId()).thenReturn(userId);
        when(storeAccessService.roles(actor)).thenReturn(Set.of(RoleName.CASHIER));
        when(storeAccessService.getActiveAssignedStoreIds(userId)).thenReturn(stores);
        when(repository.count(tenantId, stores, false, storeId, "INV-102")).thenReturn(0L);

        var result = service.search(storeId, "  INV-102  ", 0, 10, authentication);

        verify(storeAccessService).requireStoreAccess(authentication, storeId);
        assertThat(result.content()).isEmpty();
        assertThat(result.last()).isTrue();
    }

    @Test
    void ownerScopeRemainsTenantBoundWithoutStoreAssignments() {
        when(storeAccessService.roles(actor)).thenReturn(Set.of(RoleName.OWNER));
        when(repository.count(tenantId, Set.of(), true, null, "1001")).thenReturn(1L);
        when(repository.find(tenantId, Set.of(), true, null, "1001", 0, 10))
                .thenReturn(List.of(summary("1001")));

        var result = service.search(null, "1001", 0, 10, authentication);

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content().getFirst().receiptNumber()).isEqualTo("1001");
    }

    private InvoiceHistorySummary summary(String number) {
        return new InvoiceHistorySummary(UUID.randomUUID(), number, Instant.parse("2026-10-12T12:00:00Z"),
                RegisterType.RETAIL, storeId, "Main", UUID.randomUUID(), "R1", "Cashier", null, null,
                new BigDecimal("20.00"), "CAD", "DEBIT", SaleStatus.COMPLETED);
    }
}
