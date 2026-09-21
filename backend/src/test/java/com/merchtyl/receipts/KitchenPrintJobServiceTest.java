package com.merchtyl.receipts;

import com.merchtyl.sales.Sale;
import com.merchtyl.sales.SaleRepository;
import com.merchtyl.security.StoreAccessService;
import com.merchtyl.store.Store;
import org.junit.jupiter.api.*;
import org.springframework.security.core.Authentication;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class KitchenPrintJobServiceTest {
    private static final Instant NOW=Instant.parse("2026-09-20T20:00:00Z");
    private static final UUID TENANT=UUID.randomUUID(), STORE=UUID.randomUUID(), SALE=UUID.randomUUID();
    private final KitchenPrintJobRepository repository=mock(KitchenPrintJobRepository.class);
    private final KitchenTicketService tickets=mock(KitchenTicketService.class);
    private final StoreAccessService access=mock(StoreAccessService.class);
    private final SaleRepository sales=mock(SaleRepository.class);
    private final Authentication authentication=mock(Authentication.class);
    private final KitchenPrintJobService service=new KitchenPrintJobService(repository,tickets,access,sales,Clock.fixed(NOW,ZoneOffset.UTC));

    @Test void scheduledOrderUsesConfiguredLeadTimeAndPersistsOnlyOneJob() {
        Sale sale=mock(Sale.class); Store store=mock(Store.class);
        when(sale.getId()).thenReturn(SALE); when(sale.getStore()).thenReturn(store); when(sale.isPickupAsap()).thenReturn(false);
        when(sale.getPickupAt()).thenReturn(Instant.parse("2026-09-20T22:00:00Z"));
        when(store.getId()).thenReturn(STORE); when(store.getTenantId()).thenReturn(TENANT);
        when(store.isAutoPrintScheduledKitchenTickets()).thenReturn(true); when(store.getScheduledKitchenPrintLeadMinutes()).thenReturn(10);
        when(repository.findBySaleIdAndTypeForUpdate(SALE,PrintDocumentType.KITCHEN_TICKET)).thenReturn(Optional.empty());

        service.scheduleForConfirmedOrder(sale);

        var captor=org.mockito.ArgumentCaptor.forClass(KitchenPrintJob.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getScheduledAt()).isEqualTo("2026-09-20T21:50:00Z");
        assertThat(captor.getValue().getStatus()).isEqualTo(KitchenPrintJobStatus.SCHEDULED);
    }

    @Test void repeatedClaimsCannotRedispatchAClaimedJobAndSuccessfulAckIsFinal() {
        KitchenPrintJob job=new KitchenPrintJob(TENANT,STORE,SALE,NOW.minusSeconds(1));
        when(repository.findFirstByStoreIdAndStatusInAndScheduledAtLessThanEqualOrderByScheduledAtAscIdAsc(eq(STORE),anyCollection(),eq(NOW)))
                .thenReturn(Optional.of(job),Optional.empty());
        when(tickets.get(SALE,false,authentication)).thenReturn(new KitchenTicketDto(PrintDocumentType.KITCHEN_TICKET,
                SALE,"A001","Store","Kitchen","Cashier",NOW,"PHONE ORDER",null,List.of(),null,false));

        var dispatch=service.claimDue(STORE,"register-a",authentication).orElseThrow();
        assertThat(service.claimDue(STORE,"register-b",authentication)).isEmpty();
        when(repository.findByIdForUpdate(job.getId())).thenReturn(Optional.of(job));
        var acknowledged=service.acknowledge(dispatch.job().id(),new KitchenPrintAcknowledgeRequest("register-a",true,null),authentication);

        assertThat(acknowledged.status()).isEqualTo(KitchenPrintJobStatus.PRINTED);
        assertThat(acknowledged.attemptCount()).isEqualTo(1);
        assertThat(acknowledged.printedAt()).isEqualTo(NOW);
    }

    @Test void failedTransportIsNotPrintedAndGetsControlledRetryTime() {
        KitchenPrintJob job=new KitchenPrintJob(TENANT,STORE,SALE,NOW);
        job.claim("register-a",NOW,KitchenPrintOrigin.AUTOMATIC);
        when(repository.findByIdForUpdate(job.getId())).thenReturn(Optional.of(job));

        var result=service.acknowledge(job.getId(),new KitchenPrintAcknowledgeRequest("register-a",false,"offline"),authentication);

        assertThat(result.status()).isEqualTo(KitchenPrintJobStatus.FAILED);
        assertThat(result.printedAt()).isNull();
        assertThat(result.lastError()).isEqualTo("offline");
        assertThat(result.scheduledAt()).isEqualTo(NOW.plusSeconds(15));
    }

    @Test void cancellationPreservesPrintedHistoryButStopsPendingJobs() {
        KitchenPrintJob pending=new KitchenPrintJob(TENANT,STORE,SALE,NOW);
        when(repository.findBySaleIdAndTypeForUpdate(SALE,PrintDocumentType.KITCHEN_TICKET)).thenReturn(Optional.of(pending));
        service.cancelForOrder(SALE);
        assertThat(pending.getStatus()).isEqualTo(KitchenPrintJobStatus.CANCELLED);

        KitchenPrintJob printed=new KitchenPrintJob(TENANT,STORE,SALE,NOW);
        printed.claim("register-a",NOW,KitchenPrintOrigin.AUTOMATIC); printed.acknowledge(true,null,NOW);
        when(repository.findBySaleIdAndTypeForUpdate(SALE,PrintDocumentType.KITCHEN_TICKET)).thenReturn(Optional.of(printed));
        service.cancelForOrder(SALE);
        assertThat(printed.getStatus()).isEqualTo(KitchenPrintJobStatus.PRINTED);
    }
}
