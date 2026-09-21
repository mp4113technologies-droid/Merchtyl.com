package com.merchtyl.receipts;

import com.merchtyl.register.Register;
import com.merchtyl.register.RegisterType;
import com.merchtyl.sales.KitchenOrderStatus;
import com.merchtyl.sales.Sale;
import com.merchtyl.sales.SaleRepository;
import com.merchtyl.sales.SaleStatus;
import com.merchtyl.security.StoreAccessService;
import com.merchtyl.security.User;
import com.merchtyl.store.Store;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KitchenTicketServiceTest {
    private final SaleRepository saleRepository = mock(SaleRepository.class);
    private final StoreAccessService storeAccessService = mock(StoreAccessService.class);
    private final Authentication authentication = mock(Authentication.class);
    private final KitchenTicketService service = new KitchenTicketService(saleRepository, storeAccessService);

    @Test
    void confirmedPhoneOrderProducesImmediateKitchenTicketWithPickupContext() {
        UUID saleId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        Instant confirmedAt = Instant.parse("2026-09-19T14:00:00Z");
        Instant pickupAt = Instant.parse("2026-09-19T16:30:00Z");
        Sale sale = phoneOrder(saleId, storeId, SaleStatus.PHONE_CONFIRMED, KitchenOrderStatus.PENDING,
                confirmedAt, pickupAt);
        when(saleRepository.findById(saleId)).thenReturn(Optional.of(sale));

        KitchenTicketDto ticket = service.get(saleId, false, authentication);

        assertThat(ticket.orderType()).isEqualTo("PHONE ORDER");
        assertThat(ticket.tokenNumber()).isEqualTo("1045");
        assertThat(ticket.customerName()).isEqualTo("John Smith");
        assertThat(ticket.pickupAt()).isEqualTo(pickupAt);
        assertThat(ticket.orderTime()).isEqualTo(confirmedAt);
        assertThat(ticket.kitchenStatus()).isEqualTo(KitchenOrderStatus.PENDING);
        assertThat(ticket.orderNotes()).isEqualTo("Call on arrival");
        verify(storeAccessService).requireStoreAccess(authentication, storeId);
    }

    @Test
    void cancelledPhoneOrderRemainsPrintableAsKitchenCancellation() {
        UUID saleId = UUID.randomUUID();
        Sale sale = phoneOrder(saleId, UUID.randomUUID(), SaleStatus.CANCELLED,
                KitchenOrderStatus.CANCELLED, Instant.parse("2026-09-19T14:00:00Z"),
                Instant.parse("2026-09-19T16:30:00Z"));
        when(saleRepository.findById(saleId)).thenReturn(Optional.of(sale));

        KitchenTicketDto ticket = service.get(saleId, false, authentication);

        assertThat(ticket.kitchenStatus()).isEqualTo(KitchenOrderStatus.CANCELLED);
        assertThat(ticket.orderType()).isEqualTo("PHONE ORDER");
    }

    private static Sale phoneOrder(UUID saleId, UUID storeId, SaleStatus status,
                                   KitchenOrderStatus kitchenStatus, Instant confirmedAt, Instant pickupAt) {
        Sale sale = mock(Sale.class);
        Store store = mock(Store.class);
        Register register = mock(Register.class);
        User creator = mock(User.class);
        when(sale.getId()).thenReturn(saleId);
        when(sale.getStore()).thenReturn(store);
        when(store.getId()).thenReturn(storeId);
        when(store.getName()).thenReturn("Main Store");
        when(store.getTimezone()).thenReturn("America/Moncton");
        when(sale.getRegister()).thenReturn(register);
        when(register.getType()).thenReturn(RegisterType.FOOD_SERVICE);
        when(register.getName()).thenReturn("Restaurant 1");
        when(sale.getCreatedBy()).thenReturn(creator);
        when(creator.getDisplayName()).thenReturn("Cashier One");
        when(sale.getStatus()).thenReturn(status);
        when(sale.getFoodOrderToken()).thenReturn("1045");
        when(sale.getPhoneConfirmedAt()).thenReturn(confirmedAt);
        when(sale.getPhoneCustomerName()).thenReturn("John Smith");
        when(sale.getPickupAt()).thenReturn(pickupAt);
        when(sale.getKitchenStatus()).thenReturn(kitchenStatus);
        when(sale.getOrderNotes()).thenReturn("Call on arrival");
        when(sale.getItems()).thenReturn(List.of());
        return sale;
    }
}
