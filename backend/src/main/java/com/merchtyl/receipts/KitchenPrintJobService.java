package com.merchtyl.receipts;

import com.merchtyl.common.ConflictException;
import com.merchtyl.common.NotFoundException;
import com.merchtyl.sales.Sale;
import com.merchtyl.sales.SaleRepository;
import com.merchtyl.sales.SaleStatus;
import com.merchtyl.security.StoreAccessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
public class KitchenPrintJobService {
    private final KitchenPrintJobRepository repository;
    private final KitchenTicketService ticketService;
    private final StoreAccessService storeAccessService;
    private final SaleRepository saleRepository;
    private final Clock clock;

    @Autowired
    public KitchenPrintJobService(KitchenPrintJobRepository repository, KitchenTicketService ticketService,
            StoreAccessService storeAccessService, SaleRepository saleRepository) {
        this(repository, ticketService, storeAccessService, saleRepository, Clock.systemUTC());
    }

    KitchenPrintJobService(KitchenPrintJobRepository repository, KitchenTicketService ticketService,
            StoreAccessService storeAccessService, SaleRepository saleRepository, Clock clock) {
        this.repository=repository; this.ticketService=ticketService; this.storeAccessService=storeAccessService;
        this.saleRepository=saleRepository; this.clock=clock;
    }

    @Transactional
    public void scheduleForConfirmedOrder(Sale sale) {
        boolean enabled = sale.isPickupAsap() ? sale.getStore().isAutoPrintAsapKitchenTickets()
                : sale.getStore().isAutoPrintScheduledKitchenTickets();
        if (!enabled) return;
        Instant scheduledAt = sale.isPickupAsap() ? Instant.now(clock)
                : sale.getPickupAt().minus(Duration.ofMinutes(sale.getStore().getScheduledKitchenPrintLeadMinutes()));
        KitchenPrintJob job = repository.findBySaleIdAndTypeForUpdate(sale.getId(), PrintDocumentType.KITCHEN_TICKET)
                .orElseGet(() -> new KitchenPrintJob(sale.getStore().getTenantId(), sale.getStore().getId(), sale.getId(), scheduledAt));
        job.reschedule(scheduledAt);
        repository.save(job);
    }

    @Transactional
    public void cancelForOrder(UUID saleId) {
        repository.findBySaleIdAndTypeForUpdate(saleId, PrintDocumentType.KITCHEN_TICKET).ifPresent(KitchenPrintJob::cancel);
    }

    @Transactional
    public Optional<KitchenPrintDispatchDto> claimDue(UUID storeId, String clientId, Authentication authentication) {
        storeAccessService.requireStoreAccess(authentication, storeId);
        return repository.findFirstByStoreIdAndStatusInAndScheduledAtLessThanEqualOrderByScheduledAtAscIdAsc(storeId,
                List.of(KitchenPrintJobStatus.SCHEDULED, KitchenPrintJobStatus.FAILED), Instant.now(clock))
                .map(job -> dispatch(job, clientId, KitchenPrintOrigin.AUTOMATIC, authentication));
    }

    @Transactional
    public KitchenPrintDispatchDto printNow(UUID saleId, String clientId, Authentication authentication) {
        KitchenPrintJob job = repository.findBySaleIdAndTypeForUpdate(saleId, PrintDocumentType.KITCHEN_TICKET)
                .orElseGet(() -> createManualJob(saleId));
        storeAccessService.requireStoreAccess(authentication, job.getStoreId());
        if (job.getStatus() == KitchenPrintJobStatus.PRINTED) throw new ConflictException("KITCHEN_TICKET_ALREADY_PRINTED_USE_REPRINT");
        if (job.getStatus() == KitchenPrintJobStatus.CANCELLED) throw new ConflictException("KITCHEN_PRINT_JOB_CANCELLED");
        if (job.getStatus() == KitchenPrintJobStatus.DISPATCHED) throw new ConflictException("KITCHEN_PRINT_JOB_ALREADY_CLAIMED");
        return dispatch(job, clientId, KitchenPrintOrigin.MANUAL, authentication);
    }

    private KitchenPrintJob createManualJob(UUID saleId) {
        Sale sale=saleRepository.findByIdForUpdate(saleId).orElseThrow(() -> new NotFoundException("Sale not found"));
        Optional<KitchenPrintJob> existing=repository.findBySaleIdAndTypeForUpdate(saleId,PrintDocumentType.KITCHEN_TICKET);
        if(existing.isPresent()) return existing.get();
        if(sale.getStatus()!=SaleStatus.PHONE_CONFIRMED) throw new ConflictException("KITCHEN_PRINT_REQUIRES_ACTIVE_PHONE_ORDER");
        return repository.saveAndFlush(new KitchenPrintJob(sale.getStore().getTenantId(),sale.getStore().getId(),saleId,Instant.now(clock)));
    }

    private KitchenPrintDispatchDto dispatch(KitchenPrintJob job, String clientId, KitchenPrintOrigin origin, Authentication authentication) {
        job.claim(clientId, Instant.now(clock), origin);
        return new KitchenPrintDispatchDto(KitchenPrintJobDto.from(job), ticketService.get(job.getSaleId(), false, authentication), "KITCHEN", 1);
    }

    @Transactional
    public KitchenPrintJobDto acknowledge(UUID jobId, KitchenPrintAcknowledgeRequest request, Authentication authentication) {
        KitchenPrintJob job = repository.findByIdForUpdate(jobId).orElseThrow(() -> new NotFoundException("Kitchen print job not found"));
        storeAccessService.requireStoreAccess(authentication, job.getStoreId());
        if (job.getStatus() == KitchenPrintJobStatus.PRINTED) return KitchenPrintJobDto.from(job);
        if (job.getStatus() != KitchenPrintJobStatus.DISPATCHED || !request.clientId().equals(job.getClaimedBy()))
            throw new ConflictException("KITCHEN_PRINT_JOB_NOT_CLAIMED_BY_CLIENT");
        job.acknowledge(request.success(), cleanError(request.error()), Instant.now(clock));
        return KitchenPrintJobDto.from(job);
    }

    @Transactional(readOnly=true)
    public List<KitchenPrintJobDto> list(UUID storeId, Authentication authentication) {
        storeAccessService.requireStoreAccess(authentication, storeId);
        return repository.findByStoreIdOrderByScheduledAtAsc(storeId).stream().map(KitchenPrintJobDto::from).toList();
    }

    @Scheduled(fixedDelayString="${merchtyl.kitchen-print.recovery-delay-ms:60000}")
    @Transactional
    public void recoverAbandonedClaims() {
        Instant now=Instant.now(clock);
        repository.findByStatusAndLastAttemptAtBefore(KitchenPrintJobStatus.DISPATCHED, now.minusSeconds(120))
                .forEach(job -> job.recover(now.plusSeconds(15)));
    }

    private static String cleanError(String error) {
        if (error == null || error.isBlank()) return "Printer transport reported failure";
        String clean=error.trim(); return clean.length() <= 1000 ? clean : clean.substring(0,1000);
    }
}
