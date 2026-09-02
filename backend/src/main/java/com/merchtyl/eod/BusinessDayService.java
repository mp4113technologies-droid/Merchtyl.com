package com.merchtyl.eod;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.cash.CashLedgerBreakdownResponse;
import com.merchtyl.cash.CashLedgerDirection;
import com.merchtyl.cash.CashLedgerService;
import com.merchtyl.cash.CashMovement;
import com.merchtyl.cash.CashMovementRepository;
import com.merchtyl.cash.CashMovementType;
import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.ConflictException;
import com.merchtyl.common.NotFoundException;
import com.merchtyl.common.PageResponse;
import com.merchtyl.features.FeatureCode;
import com.merchtyl.features.FeatureService;
import com.merchtyl.inventory.InventoryBalance;
import com.merchtyl.inventory.InventoryBalanceRepository;
import com.merchtyl.inventory.InventoryTransaction;
import com.merchtyl.inventory.InventoryTransactionRepository;
import com.merchtyl.inventory.InventoryTransactionType;
import com.merchtyl.lottery.LotteryPayout;
import com.merchtyl.lottery.LotteryPayoutMethod;
import com.merchtyl.lottery.LotteryPayoutRepository;
import com.merchtyl.lottery.LotteryPayoutReversal;
import com.merchtyl.lottery.LotteryPayoutReversalRepository;
import com.merchtyl.lottery.LotteryPayoutStatus;
import com.merchtyl.lottery.LotterySale;
import com.merchtyl.lottery.LotterySaleCancellation;
import com.merchtyl.lottery.LotterySaleCancellationRepository;
import com.merchtyl.lottery.LotterySaleRepository;
import com.merchtyl.lottery.LotterySaleStatus;
import com.merchtyl.lottery.LotterySettlement;
import com.merchtyl.lottery.LotterySettlementRepository;
import com.merchtyl.lottery.LotterySettlementStatus;
import com.merchtyl.refunds.Refund;
import com.merchtyl.refunds.RefundPayment;
import com.merchtyl.refunds.RefundRepository;
import com.merchtyl.registersession.RegisterSession;
import com.merchtyl.registersession.RegisterSessionRepository;
import com.merchtyl.registersession.RegisterSessionStatus;
import com.merchtyl.sales.Payment;
import com.merchtyl.sales.PaymentMethod;
import com.merchtyl.sales.Sale;
import com.merchtyl.sales.SaleItem;
import com.merchtyl.sales.SaleRepository;
import com.merchtyl.sales.SaleStatus;
import com.merchtyl.security.User;
import com.merchtyl.security.UserRepository;
import com.merchtyl.security.StoreAccessService;
import com.merchtyl.store.Store;
import com.merchtyl.store.StoreRepository;
import jakarta.persistence.OptimisticLockException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BusinessDayService {
    private static final Logger log = LoggerFactory.getLogger(BusinessDayService.class);
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MONEY_SCALE = 2;
    private static final int QUANTITY_SCALE = 4;
    private static final Collection<BusinessDayStatus> ACTIVE_DAY_STATUSES = List.of(
            BusinessDayStatus.OPEN,
            BusinessDayStatus.CLOSING,
            BusinessDayStatus.REOPENED);
    private static final Set<SaleStatus> FINANCIALLY_POSTED_SALE_STATUSES = Set.of(
            SaleStatus.COMPLETED,
            SaleStatus.PARTIALLY_REFUNDED,
            SaleStatus.REFUNDED);

    private final BusinessDayRepository businessDayRepository;
    private final EndOfDayReportRepository reportRepository;
    private final BusinessDayConfigurationRepository configurationRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final RegisterSessionRepository registerSessionRepository;
    private final SaleRepository saleRepository;
    private final RefundRepository refundRepository;
    private final CashMovementRepository cashMovementRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final InventoryBalanceRepository inventoryBalanceRepository;
    private final LotterySaleRepository lotterySaleRepository;
    private final LotteryPayoutRepository lotteryPayoutRepository;
    private final LotterySaleCancellationRepository lotterySaleCancellationRepository;
    private final LotteryPayoutReversalRepository lotteryPayoutReversalRepository;
    private final LotterySettlementRepository lotterySettlementRepository;
    private final CashLedgerService cashLedgerService;
    private final FeatureService featureService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    @Autowired(required = false)
    private StoreAccessService storeAccessService;

    @Autowired
    public BusinessDayService(
            BusinessDayRepository businessDayRepository,
            EndOfDayReportRepository reportRepository,
            BusinessDayConfigurationRepository configurationRepository,
            StoreRepository storeRepository,
            UserRepository userRepository,
            RegisterSessionRepository registerSessionRepository,
            SaleRepository saleRepository,
            RefundRepository refundRepository,
            CashMovementRepository cashMovementRepository,
            InventoryTransactionRepository inventoryTransactionRepository,
            InventoryBalanceRepository inventoryBalanceRepository,
            LotterySaleRepository lotterySaleRepository,
            LotteryPayoutRepository lotteryPayoutRepository,
            LotterySaleCancellationRepository lotterySaleCancellationRepository,
            LotteryPayoutReversalRepository lotteryPayoutReversalRepository,
            LotterySettlementRepository lotterySettlementRepository,
            CashLedgerService cashLedgerService,
            FeatureService featureService,
            AuditService auditService,
            ObjectMapper objectMapper) {
        this(
                businessDayRepository,
                reportRepository,
                configurationRepository,
                storeRepository,
                userRepository,
                registerSessionRepository,
                saleRepository,
                refundRepository,
                cashMovementRepository,
                inventoryTransactionRepository,
                inventoryBalanceRepository,
                lotterySaleRepository,
                lotteryPayoutRepository,
                lotterySaleCancellationRepository,
                lotteryPayoutReversalRepository,
                lotterySettlementRepository,
                cashLedgerService,
                featureService,
                auditService,
                objectMapper,
                Clock.systemUTC());
    }

    BusinessDayService(
            BusinessDayRepository businessDayRepository,
            EndOfDayReportRepository reportRepository,
            BusinessDayConfigurationRepository configurationRepository,
            StoreRepository storeRepository,
            UserRepository userRepository,
            RegisterSessionRepository registerSessionRepository,
            SaleRepository saleRepository,
            RefundRepository refundRepository,
            CashMovementRepository cashMovementRepository,
            InventoryTransactionRepository inventoryTransactionRepository,
            InventoryBalanceRepository inventoryBalanceRepository,
            LotterySaleRepository lotterySaleRepository,
            LotteryPayoutRepository lotteryPayoutRepository,
            LotterySaleCancellationRepository lotterySaleCancellationRepository,
            LotteryPayoutReversalRepository lotteryPayoutReversalRepository,
            LotterySettlementRepository lotterySettlementRepository,
            CashLedgerService cashLedgerService,
            FeatureService featureService,
            AuditService auditService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.businessDayRepository = businessDayRepository;
        this.reportRepository = reportRepository;
        this.configurationRepository = configurationRepository;
        this.storeRepository = storeRepository;
        this.userRepository = userRepository;
        this.registerSessionRepository = registerSessionRepository;
        this.saleRepository = saleRepository;
        this.refundRepository = refundRepository;
        this.cashMovementRepository = cashMovementRepository;
        this.inventoryTransactionRepository = inventoryTransactionRepository;
        this.inventoryBalanceRepository = inventoryBalanceRepository;
        this.lotterySaleRepository = lotterySaleRepository;
        this.lotteryPayoutRepository = lotteryPayoutRepository;
        this.lotterySaleCancellationRepository = lotterySaleCancellationRepository;
        this.lotteryPayoutReversalRepository = lotteryPayoutReversalRepository;
        this.lotterySettlementRepository = lotterySettlementRepository;
        this.cashLedgerService = cashLedgerService;
        this.featureService = featureService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public BusinessDayResponse open(BusinessDayOpenRequest request, Authentication authentication) {
        if (request == null || request.storeId() == null) {
            throw new BadRequestException("storeId is required");
        }
        Store store = store(request.storeId());
        User actor = currentUser(authentication);
        requireStoreManagement(authentication, store.getId());
        BusinessDayConfiguration configuration = configuration(store);
        LocalDate businessDate = request.businessDate() == null
                ? Instant.now(clock).atZone(ZoneId.of(store.getTimezone())).toLocalDate()
                : request.businessDate();
        List<BusinessDay> activeDays = businessDayRepository.findByStore_IdAndStatusIn(store.getId(), ACTIVE_DAY_STATUSES);
        if (!activeDays.isEmpty() && !(request.overrideOpenPrevious() && !configuration.isBlockNextBusinessDayUntilPreviousClose())) {
            throw new ConflictException("Previous business day remains open for this store");
        }
        if (request.overrideOpenPrevious() && (request.overrideReason() == null || request.overrideReason().isBlank())) {
            throw new BadRequestException("overrideReason is required when using an override");
        }
        if (businessDayRepository.existsByStore_IdAndBusinessDate(store.getId(), businessDate)) {
            throw new ConflictException("Business day already exists for this store and date");
        }
        BusinessDay saved = saveDay(new BusinessDay(store, businessDate, store.getTimezone(), actor, Instant.now(clock)));
        BusinessDayResponse response = BusinessDayResponse.from(saved);
        audit(actor, AuditAction.BUSINESS_DAY_OPENED, saved, null, response, request.overrideReason());
        log.info("business_day_event event=BUSINESS_DAY_OPENED tenant_id={} store_id={} business_day_id={} business_date={} actor_user_id={}",
                actor.getTenantId(), saved.getStore().getId(), saved.getId(), saved.getBusinessDate(), actor.getId());
        return response;
    }

    @Transactional(readOnly = true)
    public BusinessDayResponse current(UUID storeId) {
        if (storeId == null) {
            throw new BadRequestException("storeId is required");
        }
        Store store = store(storeId);
        return businessDayRepository.findByStore_IdAndBusinessDate(storeId, currentBusinessDate(store))
                .filter(day -> ACTIVE_DAY_STATUSES.contains(day.getStatus()))
                .map(BusinessDayResponse::from)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public BusinessDayResponse current(UUID storeId, Authentication authentication) {
        requireStoreAccess(authentication, storeId);
        return current(storeId);
    }

    @Transactional(readOnly = true)
    public BusinessDayOperationalStateResponse operationalState(UUID storeId, Authentication authentication) {
        requireStoreAccess(authentication, storeId);
        Store store = store(storeId);
        LocalDate currentBusinessDate = currentBusinessDate(store);
        BusinessDay currentDay = businessDayRepository.findByStore_IdAndBusinessDate(storeId, currentBusinessDate).orElse(null);
        BusinessDay latestDay = businessDayRepository.findFirstByStore_IdOrderByBusinessDateDescOpenedAtDesc(storeId).orElse(null);

        if (currentDay != null) {
            boolean closed = currentDay.getStatus() == BusinessDayStatus.CLOSED;
            return new BusinessDayOperationalStateResponse(
                    storeId,
                    currentBusinessDate,
                    BusinessDayResponse.from(currentDay),
                    latestDay != null && !latestDay.getId().equals(currentDay.getId()) ? BusinessDayResponse.from(latestDay) : null,
                    closed ? BusinessDayOperationalState.CLOSED_TODAY : BusinessDayOperationalState.OPEN,
                    closed ? BusinessDayAvailableAction.REOPEN : BusinessDayAvailableAction.NONE);
        }

        BusinessDay previousOpenDay = businessDayRepository
                .findFirstByStore_IdAndStatusInOrderByBusinessDateDescOpenedAtDesc(storeId, ACTIVE_DAY_STATUSES)
                .orElse(null);
        if (previousOpenDay != null) {
            return new BusinessDayOperationalStateResponse(
                    storeId,
                    currentBusinessDate,
                    null,
                    BusinessDayResponse.from(previousOpenDay),
                    BusinessDayOperationalState.PREVIOUS_DAY_STILL_OPEN,
                    BusinessDayAvailableAction.NONE);
        }
        return new BusinessDayOperationalStateResponse(
                storeId,
                currentBusinessDate,
                null,
                latestDay == null ? null : BusinessDayResponse.from(latestDay),
                    latestDay == null ? BusinessDayOperationalState.NO_BUSINESS_DAY_TODAY : BusinessDayOperationalState.HISTORICAL_CLOSED,
                    BusinessDayAvailableAction.OPEN);
    }

    @Transactional(readOnly = true)
    public BusinessDayResponse latest(UUID storeId) {
        if (storeId == null) {
            throw new BadRequestException("storeId is required");
        }
        return businessDayRepository.findFirstByStore_IdOrderByBusinessDateDescOpenedAtDesc(storeId)
                .map(BusinessDayResponse::from)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public BusinessDayResponse latest(UUID storeId, Authentication authentication) {
        requireStoreAccess(authentication, storeId);
        return latest(storeId);
    }

    private LocalDate currentBusinessDate(Store store) {
        return Instant.now(clock).atZone(ZoneId.of(store.getTimezone())).toLocalDate();
    }

    public void assertStoreAccess(UUID storeId, Authentication authentication) {
        if (storeId == null) {
            throw new BadRequestException("storeId is required");
        }
        requireStoreAccess(authentication, storeId);
    }

    @Transactional(readOnly = true)
    public PageResponse<BusinessDayResponse> search(UUID storeId, LocalDate dateFrom, LocalDate dateTo, BusinessDayStatus status, int page, int size) {
        var result = businessDayRepository.findAll(
                Specification
                        .where(equalReference("store", storeId))
                        .and(dateGreaterThanOrEqualTo(dateFrom))
                        .and(dateLessThanOrEqualTo(dateTo))
                        .and(equalEnum("status", status)),
                PageRequest.of(Math.max(0, page), Math.max(1, Math.min(MAX_PAGE_SIZE, size)),
                        Sort.by(Sort.Direction.DESC, "businessDate").and(Sort.by(Sort.Direction.DESC, "id"))));
        return new PageResponse<>(
                result.getContent().stream().map(BusinessDayResponse::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast());
    }

    @Transactional(readOnly = true)
    public BusinessDayResponse get(UUID id) {
        return BusinessDayResponse.from(day(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<EndOfDayReportResponse> searchReports(EndOfDayReportSearchRequest request) {
        EndOfDayReportSearchRequest normalized = request == null
                ? new EndOfDayReportSearchRequest(null, null, null, null, null, null, 0, 20)
                : request;
        var result = reportRepository.findAll(
                Specification
                        .where(reportStore(normalized.storeId()))
                        .and(reportDateGreaterThanOrEqualTo(normalized.dateFrom()))
                        .and(reportDateLessThanOrEqualTo(normalized.dateTo()))
                        .and(reportBusinessDayStatus(normalized.status()))
                        .and(reportClosedBy(normalized.closedBy()))
                        .and(reportNumber(normalized.reportNumber())),
                PageRequest.of(
                        Math.max(0, normalized.page()),
                        Math.max(1, Math.min(MAX_PAGE_SIZE, normalized.size())),
                        Sort.by(Sort.Direction.DESC, "businessDate")
                                .and(Sort.by(Sort.Direction.DESC, "generatedAt"))
                                .and(Sort.by(Sort.Direction.DESC, "revision"))));
        return new PageResponse<>(
                result.getContent().stream().map(EndOfDayReportResponse::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast());
    }

    @Transactional(readOnly = true)
    public EndOfDayReportResponse getReport(UUID id) {
        return EndOfDayReportResponse.from(report(id));
    }

    @Transactional
    public BusinessDayResponse startClosing(UUID id, Authentication authentication) {
        BusinessDay day = dayForUpdate(id);
        User actor = currentUser(authentication);
        requireStoreManagement(authentication, day.getStore().getId());
        if (day.getStatus() == BusinessDayStatus.CLOSED) {
            throw new ConflictException("Business day is already closed");
        }
        if (day.getStatus() != BusinessDayStatus.CLOSING) {
            day.startClosing(actor, Instant.now(clock));
            saveDay(day);
        }
        BusinessDayResponse response = BusinessDayResponse.from(day);
        audit(actor, AuditAction.BUSINESS_DAY_CLOSING_STARTED, day, null, response, null);
        return response;
    }

    @Transactional(readOnly = true)
    public ClosingValidationResponse validateClosing(UUID id) {
        BusinessDay day = day(id);
        return validate(day, false);
    }

    @Transactional(readOnly = true)
    public EndOfDayClosingPreviewResponse previewClosing(UUID id) {
        BusinessDay day = day(id);
        if (day.getStatus() != BusinessDayStatus.OPEN
                && day.getStatus() != BusinessDayStatus.CLOSING
                && day.getStatus() != BusinessDayStatus.REOPENED) {
            throw new ConflictException("Business day must be open or closing to preview end-of-day closing");
        }
        return previewResponse(day, aggregate(day));
    }

    @Transactional
    public EndOfDayReportResponse close(UUID id, BusinessDayCloseRequest request, Authentication authentication) {
        BusinessDay day = dayForUpdate(id);
        User actor = currentUser(authentication);
        requireStoreManagement(authentication, day.getStore().getId());
        requireVersion(day, request == null ? null : request.version());
        if (day.getStatus() == BusinessDayStatus.CLOSED) {
            return reportRepository.findFirstByBusinessDay_IdOrderByRevisionDesc(day.getId())
                    .map(EndOfDayReportResponse::from)
                    .orElseThrow(() -> new ConflictException("Business day is closed but no report exists"));
        }
        ClosingValidationResponse validation = validate(day, false);
        if (!validation.closable()) {
            audit(actor, AuditAction.BUSINESS_DAY_CLOSING_VALIDATION_FAILED, day, null, validation, null);
            throw new ClosingValidationException(validation);
        }
        return generateAndClose(day, actor, request.managerNotes(), request.varianceExplanation(), request.confirmationAccepted(), null);
    }

    @Transactional
    public EndOfDayReportResponse forceClose(UUID id, BusinessDayForceCloseRequest request, Authentication authentication) {
        BusinessDay day = dayForUpdate(id);
        User actor = currentUser(authentication);
        requireStoreManagement(authentication, day.getStore().getId());
        BusinessDayConfiguration configuration = configuration(day.getStore());
        if (!configuration.isAllowForceClose()) {
            throw new ConflictException("Force close is disabled for this store");
        }
        requireVersion(day, request == null ? null : request.version());
        String reason = cleanRequired(request.reason(), "reason");
        if (day.getStatus() == BusinessDayStatus.CLOSED) {
            return reportRepository.findFirstByBusinessDay_IdOrderByRevisionDesc(day.getId())
                    .map(EndOfDayReportResponse::from)
                    .orElseThrow(() -> new ConflictException("Business day is closed but no report exists"));
        }
        return generateAndClose(day, actor, request.managerNotes(), request.varianceExplanation(), request.confirmationAccepted(), reason);
    }

    @Transactional
    public BusinessDayResponse reopen(UUID id, BusinessDayReopenRequest request, Authentication authentication) {
        BusinessDay day = dayForUpdate(id);
        User actor = currentUser(authentication);
        requireStoreManagement(authentication, day.getStore().getId());
        requireVersion(day, request == null ? null : request.version());
        if (day.getStatus() != BusinessDayStatus.CLOSED) {
            throw new ConflictException("Only closed business days can be reopened");
        }
        if (!day.getBusinessDate().equals(currentBusinessDate(day.getStore()))) {
            audit(actor, AuditAction.BUSINESS_DAY_REOPEN_REJECTED, day, null, null, "HISTORICAL_BUSINESS_DAY");
            throw new ConflictException("HISTORICAL_BUSINESS_DAY: Only today's closed business day can be reopened");
        }
        if (!reportRepository.existsByBusinessDay_Id(day.getId())) {
            throw new ConflictException("Business day cannot be reopened before an end-of-day report exists");
        }
        if (businessDayRepository.existsByStore_IdAndBusinessDateGreaterThan(day.getStore().getId(), day.getBusinessDate())) {
            audit(actor, AuditAction.BUSINESS_DAY_REOPEN_REJECTED, day, null, null, "LATER_BUSINESS_DAY_EXISTS");
            throw new ConflictException("LATER_BUSINESS_DAY_EXISTS");
        }
        if (!businessDayRepository.findByStore_IdAndStatusIn(day.getStore().getId(), ACTIVE_DAY_STATUSES).isEmpty()) {
            audit(actor, AuditAction.BUSINESS_DAY_REOPEN_REJECTED, day, null, null, "BUSINESS_DAY_ALREADY_OPEN");
            throw new ConflictException("BUSINESS_DAY_ALREADY_OPEN");
        }
        String reason = cleanRequired(request.reason(), "reason");
        day.reopen(actor, Instant.now(clock), reason);
        saveDay(day);
        BusinessDayResponse response = BusinessDayResponse.from(day);
        audit(actor, AuditAction.BUSINESS_DAY_REOPENED, day, null, response, reason);
        log.info("business_day_event event=BUSINESS_DAY_REOPENED tenant_id={} store_id={} business_day_id={} business_date={} actor_user_id={}",
                actor.getTenantId(), day.getStore().getId(), day.getId(), day.getBusinessDate(), actor.getId());
        return response;
    }

    @Transactional
    public BusinessDay requireOpenBusinessDayForUpdate(UUID storeId) {
        if (storeId == null) {
            throw new BadRequestException("storeId is required");
        }
        Store store = store(storeId);
        BusinessDay activeDay = businessDayRepository.findActiveByStoreIdForUpdate(storeId, ACTIVE_DAY_STATUSES).stream()
                .filter(day -> day.getStatus() == BusinessDayStatus.OPEN || day.getStatus() == BusinessDayStatus.REOPENED)
                .findFirst()
                .orElseThrow(() -> new ConflictException("BUSINESS_DAY_NOT_OPEN"));
        if (!activeDay.getBusinessDate().equals(currentBusinessDate(store))) {
            throw new ConflictException("PREVIOUS_BUSINESS_DAY_STILL_OPEN");
        }
        return activeDay;
    }

    @Transactional(readOnly = true)
    public ClosingReminderResponse closingReminder(UUID storeId) {
        BusinessDayResponse current = current(storeId);
        Store store = store(storeId);
        BusinessDayConfiguration configuration = configuration(store);
        if (current == null || configuration.getClosingReminderTime() == null) {
            return new ClosingReminderResponse(storeId, current == null ? null : current.id(), false, 0, false, null);
        }
        LocalTime localTime = Instant.now(clock).atZone(ZoneId.of(store.getTimezone())).toLocalTime();
        BusinessDay currentDay = day(current.id());
        long openRegisters = registerSessions(currentDay).stream()
                .filter(session -> session.getStatus() == RegisterSessionStatus.OPEN)
                .count();
        boolean past = !localTime.isBefore(configuration.getClosingReminderTime());
        boolean ready = past && openRegisters == 0 && configuration.isAutomaticallyGenerateReportAfterFinalRegisterCloses();
        return new ClosingReminderResponse(
                storeId,
                current.id(),
                past,
                openRegisters,
                ready,
                past ? "Business day is past the configured closing reminder time" : null);
    }

    @Transactional
    public String printReport(UUID id, Authentication authentication) {
        User actor = currentUser(authentication);
        EndOfDayReport report = report(id);
        EndOfDayReportResponse response = EndOfDayReportResponse.from(report);
        auditService.record(new CreateAuditRecordCommand(
                actor.getId(),
                AuditAction.END_OF_DAY_REPORT_PRINTED,
                "END_OF_DAY_REPORT",
                report.getId(),
                report.getStore().getId(),
                null,
                null,
                Map.of("reportNumber", report.getReportNumber()),
                null));
        return printableHtml(response);
    }

    @Transactional
    public String exportCsv(UUID id, Authentication authentication) {
        User actor = currentUser(authentication);
        EndOfDayReport report = report(id);
        EndOfDayReportResponse response = EndOfDayReportResponse.from(report);
        auditService.record(new CreateAuditRecordCommand(
                actor.getId(),
                AuditAction.END_OF_DAY_REPORT_EXPORTED,
                "END_OF_DAY_REPORT",
                report.getId(),
                report.getStore().getId(),
                null,
                null,
                Map.of("format", "csv", "reportNumber", report.getReportNumber()),
                null));
        return csv(response);
    }

    @Transactional
    public byte[] exportPdf(UUID id, Authentication authentication) {
        User actor = currentUser(authentication);
        EndOfDayReport report = report(id);
        EndOfDayReportResponse response = EndOfDayReportResponse.from(report);
        auditService.record(new CreateAuditRecordCommand(
                actor.getId(),
                AuditAction.END_OF_DAY_REPORT_EXPORTED,
                "END_OF_DAY_REPORT",
                report.getId(),
                report.getStore().getId(),
                null,
                null,
                Map.of("format", "pdf", "reportNumber", report.getReportNumber()),
                null));
        return simplePdf(pdfLines(response));
    }

    private EndOfDayReportResponse generateAndClose(
            BusinessDay day,
            User actor,
            String notes,
            String varianceExplanation,
            Boolean confirmationAccepted,
            String forceCloseReason) {
        GeneratedReport generated = aggregate(day);
        BusinessDayConfiguration configuration = configuration(day.getStore());
        validateSignOff(configuration, generated.totals.cashVariance(), notes, varianceExplanation, confirmationAccepted);
        int revision = reportRepository.maxRevision(day.getId()) + 1;
        String reportNumber = "%s-%s-R%d".formatted(day.getStore().getCode(), day.getBusinessDate(), revision);
        EndOfDayReport report = new EndOfDayReport(
                day,
                actor,
                reportNumber,
                revision,
                Instant.now(clock),
                generated.totals(),
                snapshot(generated.snapshot()));
        generated.registers().forEach(values -> report.addRegisterSummary(new EndOfDayRegisterSummary(report, values.session(), values.values())));
        generated.payments().forEach(values -> report.addPaymentSummary(new EndOfDayPaymentSummary(report, values.method(), values.collected(), values.refunded(), values.net(), values.cashTendered(), values.changeGiven(), values.transactionCount(), values.splitPaymentCount())));
        generated.taxes().forEach(values -> report.addTaxSummary(new EndOfDayTaxSummary(report, values.componentCode(), values.componentName(), values.taxableSales(), values.exemptSales(), values.zeroRatedSales(), values.outOfScopeSales(), values.taxCollected(), values.taxRefunded(), values.roundingAdjustment())));
        EndOfDayLotteryValues lottery = generated.lottery();
        report.setLotterySummary(new EndOfDayLotterySummary(report, lottery.enabled(), lottery.lotterySales(), lottery.lotteryPayouts(), lottery.saleCancellations(), lottery.payoutReversals(), lottery.cashLotteryActivity(), lottery.nonCashLotteryActivity(), lottery.commissionEarned(), lottery.settlementAmount(), lottery.operatorReferrals(), lottery.pendingReferrals(), lottery.approvalCount(), lottery.rejectedPayouts(), lottery.operatorTotals(), lottery.registerTotals(), lottery.cashierTotals()));
        EndOfDayInventoryValues inventory = generated.inventory();
        report.setInventorySummary(new EndOfDayInventorySummary(report, inventory.deductedBySales(), inventory.restoredByReturns(), inventory.manualIncreases(), inventory.manualDecreases(), inventory.damagedQuantity(), inventory.expiredQuantity(), inventory.transferIn(), inventory.transferOut(), inventory.stockCountVariances(), inventory.lowStockProducts(), inventory.negativeStockProducts(), inventory.inventoryValueMovement()));
        generated.cashiers().forEach(values -> report.addCashierSummary(new EndOfDayCashierSummary(report, values.cashier(), values.cashierName(), values.transactionCount(), values.grossSales(), values.netSales(), values.refundTotal(), values.voidCount(), values.discountTotal(), values.priceOverrideCount(), values.cashHandled(), values.lotterySales(), values.lotteryPayouts(), values.averageTransactionValue(), values.firstActivityAt(), values.lastActivityAt(), values.registersUsed())));
        generated.exceptions().forEach(values -> report.addExceptionSummary(new EndOfDayExceptionSummary(report, values.type(), values.count(), values.totalAmount(), values.details())));
        report.setSignOff(new EndOfDaySignOff(report, actor, Instant.now(clock), cleanOptional(notes), cleanOptional(varianceExplanation), Boolean.TRUE.equals(confirmationAccepted)));
        EndOfDayReport savedReport = reportRepository.saveAndFlush(report);
        day.close(actor, Instant.now(clock), forceCloseReason);
        saveDay(day);
        EndOfDayReportResponse response = EndOfDayReportResponse.from(savedReport);
        audit(actor, forceCloseReason == null ? AuditAction.BUSINESS_DAY_CLOSED : AuditAction.BUSINESS_DAY_FORCE_CLOSED, day, null, response, forceCloseReason);
        auditService.record(new CreateAuditRecordCommand(actor.getId(), AuditAction.END_OF_DAY_REPORT_GENERATED, "END_OF_DAY_REPORT", savedReport.getId(), day.getStore().getId(), null, null, response, null));
        auditService.record(new CreateAuditRecordCommand(actor.getId(), AuditAction.END_OF_DAY_SIGN_OFF_COMPLETED, "END_OF_DAY_SIGN_OFF", savedReport.getSignOff().getId(), day.getStore().getId(), null, null, response.signOff(), null));
        log.info("business_day_event event={} tenant_id={} store_id={} business_day_id={} business_date={} actor_user_id={}",
                forceCloseReason == null ? "BUSINESS_DAY_CLOSED" : "BUSINESS_DAY_FORCE_CLOSED",
                actor.getTenantId(), day.getStore().getId(), day.getId(), day.getBusinessDate(), actor.getId());
        return response;
    }

    private EndOfDayClosingPreviewResponse previewResponse(BusinessDay day, GeneratedReport generated) {
        EndOfDayReportTotals totals = generated.totals();
        BusinessDayConfiguration configuration = configuration(day.getStore());
        return new EndOfDayClosingPreviewResponse(
                day.getId(),
                day.getStore().getId(),
                day.getStore().getCode(),
                day.getStore().getName(),
                day.getBusinessDate(),
                day.getStatus(),
                day.getVersion(),
                totals.grossSales(),
                totals.netSales(),
                totals.discountTotal(),
                totals.refundTotal(),
                totals.voidTotal(),
                totals.taxTotal(),
                totals.transactionCount(),
                totals.averageTransactionValue(),
                totals.highestTransactionValue(),
                totals.lowestTransactionValue(),
                totals.itemsSold(),
                totals.averageBasketSize(),
                totals.expectedCash(),
                totals.countedCash(),
                totals.cashVariance(),
                configuration.getCashVarianceExplanationThreshold(),
                totals.cashVariance().abs().compareTo(configuration.getCashVarianceExplanationThreshold()) > 0,
                configuration.isRequireManagerSignOff(),
                totals.currencyCode(),
                generated.registers().stream().map(BusinessDayService::registerPreview).toList(),
                generated.payments().stream().map(BusinessDayService::paymentPreview).toList(),
                generated.taxes().stream().map(BusinessDayService::taxPreview).toList(),
                lotteryPreview(generated.lottery()),
                inventoryPreview(generated.inventory()),
                generated.cashiers().stream().map(BusinessDayService::cashierPreview).toList(),
                generated.exceptions().stream().map(BusinessDayService::exceptionPreview).toList());
    }

    private static EndOfDayRegisterSummaryResponse registerPreview(RegisterValuesWithSession register) {
        RegisterSession session = register.session();
        RegisterSummaryValues values = register.values();
        return new EndOfDayRegisterSummaryResponse(
                session.getId(),
                session.getRegister().getId(),
                session.getRegister().getCode(),
                session.getRegister().getName(),
                values.openingFloat(),
                values.cashReceipts(),
                values.changeGiven(),
                values.cashRefunds(),
                values.lotteryCashSales(),
                values.lotteryPayouts(),
                values.lotteryPayoutReversals(),
                values.lotterySaleCancellations(),
                values.cashIn(),
                values.cashOut(),
                values.safeDrops(),
                values.floatAdditions(),
                values.floatRemovals(),
                values.expenses(),
                values.closingAdjustments(),
                values.expectedCash(),
                values.countedCash(),
                values.variance(),
                session.getAssignedCashier().getId(),
                display(session.getAssignedCashier()),
                session.getClosedBy() == null ? null : session.getClosedBy().getId(),
                session.getClosedBy() == null ? null : display(session.getClosedBy()),
                session.getOpenedAt(),
                session.getClosedAt(),
                session.getStatus() == RegisterSessionStatus.FORCE_CLOSED,
                session.getForceCloseReason());
    }

    private static EndOfDayPaymentSummaryResponse paymentPreview(EndOfDayPaymentValues payment) {
        return new EndOfDayPaymentSummaryResponse(
                payment.method(),
                payment.collected(),
                payment.refunded(),
                payment.net(),
                payment.cashTendered(),
                payment.changeGiven(),
                payment.transactionCount(),
                payment.splitPaymentCount());
    }

    private static EndOfDayTaxSummaryResponse taxPreview(EndOfDayTaxValues tax) {
        return new EndOfDayTaxSummaryResponse(
                tax.componentCode(),
                tax.componentName(),
                tax.taxableSales(),
                tax.exemptSales(),
                tax.zeroRatedSales(),
                tax.outOfScopeSales(),
                tax.taxCollected(),
                tax.taxRefunded(),
                tax.roundingAdjustment(),
                money(tax.taxCollected().subtract(tax.taxRefunded()).add(tax.roundingAdjustment())));
    }

    private static EndOfDayLotterySummaryResponse lotteryPreview(EndOfDayLotteryValues lottery) {
        return new EndOfDayLotterySummaryResponse(
                lottery.enabled(),
                lottery.lotterySales(),
                lottery.lotteryPayouts(),
                lottery.saleCancellations(),
                lottery.payoutReversals(),
                lottery.cashLotteryActivity(),
                lottery.nonCashLotteryActivity(),
                lottery.commissionEarned(),
                lottery.settlementAmount(),
                lottery.operatorReferrals(),
                lottery.pendingReferrals(),
                lottery.approvalCount(),
                lottery.rejectedPayouts(),
                lottery.operatorTotals(),
                lottery.registerTotals(),
                lottery.cashierTotals());
    }

    private static EndOfDayInventorySummaryResponse inventoryPreview(EndOfDayInventoryValues inventory) {
        return new EndOfDayInventorySummaryResponse(
                inventory.deductedBySales(),
                inventory.restoredByReturns(),
                inventory.manualIncreases(),
                inventory.manualDecreases(),
                inventory.damagedQuantity(),
                inventory.expiredQuantity(),
                inventory.transferIn(),
                inventory.transferOut(),
                inventory.stockCountVariances(),
                inventory.lowStockProducts(),
                inventory.negativeStockProducts(),
                inventory.inventoryValueMovement());
    }

    private static EndOfDayCashierSummaryResponse cashierPreview(EndOfDayCashierValues cashier) {
        return new EndOfDayCashierSummaryResponse(
                cashier.cashier().getId(),
                cashier.cashierName(),
                cashier.transactionCount(),
                cashier.grossSales(),
                cashier.netSales(),
                cashier.refundTotal(),
                cashier.voidCount(),
                cashier.discountTotal(),
                cashier.priceOverrideCount(),
                cashier.cashHandled(),
                cashier.lotterySales(),
                cashier.lotteryPayouts(),
                cashier.averageTransactionValue(),
                cashier.firstActivityAt(),
                cashier.lastActivityAt(),
                cashier.registersUsed());
    }

    private static EndOfDayExceptionSummaryResponse exceptionPreview(EndOfDayExceptionValues exception) {
        return new EndOfDayExceptionSummaryResponse(
                exception.type(),
                exception.count(),
                exception.totalAmount(),
                exception.details());
    }

    private GeneratedReport aggregate(BusinessDay day) {
        List<Sale> sales = saleRepository.findAll(saleSpec(day.getStore().getId(), day.getBusinessDate()), Sort.by("completedAt").and(Sort.by("id")));
        List<Refund> refunds = refundRepository.findAll(refundSpec(day.getStore().getId(), day.getBusinessDate()), Sort.by("occurredAt").and(Sort.by("id")));
        List<Sale> voidedSales = saleRepository.findAll(voidedSaleSpec(day.getStore().getId(), day.getBusinessDate()), Sort.by("updatedAt").and(Sort.by("id")));
        List<RegisterSession> sessions = registerSessions(day);
        Map<UUID, CashLedgerBreakdownResponse> cashBreakdowns = cashLedgerService.breakdowns(sessions);
        List<CashMovement> cashMovements = cashMovementRepository.findAll(cashMovementSpec(day.getStore().getId(), day.getBusinessDate(), day.getTimezone()), Sort.by("occurredAt").and(Sort.by("id")));
        List<InventoryTransaction> inventoryTransactions = inventoryTransactionRepository.findAll(inventoryTransactionSpec(day.getStore().getId(), day.getBusinessDate(), day.getTimezone()), Sort.by("occurredAt").and(Sort.by("id")));
        List<InventoryBalance> balances = inventoryBalanceRepository.findAll(balanceSpec(day.getStore().getId()), Sort.by("product.sku").and(Sort.by("id")));
        boolean lotteryEnabled = featureService.isEnabled(FeatureCode.LOTTERY_SALES, day.getStore().getId(), null);
        List<LotterySale> lotterySales = lotteryEnabled ? lotterySaleRepository.findAll(lotterySaleSpec(day.getStore().getId(), day.getBusinessDate(), day.getTimezone()), Sort.by("occurredAt").and(Sort.by("id"))) : List.of();
        List<LotteryPayout> lotteryPayouts = lotteryEnabled ? lotteryPayoutRepository.findAll(lotteryPayoutSpec(day.getStore().getId(), day.getBusinessDate()), Sort.by("occurredAt").and(Sort.by("id"))) : List.of();
        List<LotterySaleCancellation> cancellations = lotteryEnabled ? lotterySaleCancellationRepository.findAll(lotteryCancellationSpec(day.getStore().getId(), day.getBusinessDate(), day.getTimezone()), Sort.by("cancelledAt").and(Sort.by("id"))) : List.of();
        List<LotteryPayoutReversal> reversals = lotteryEnabled ? lotteryPayoutReversalRepository.findAll(lotteryReversalSpec(day.getStore().getId(), day.getBusinessDate(), day.getTimezone()), Sort.by("reversedAt").and(Sort.by("id"))) : List.of();
        List<LotterySettlement> settlements = lotteryEnabled ? lotterySettlementRepository.findAll(lotterySettlementSpec(day.getStore().getId(), day.getBusinessDate()), Sort.by("periodEnd").and(Sort.by("id"))) : List.of();

        BigDecimal grossSales = money(sum(sales, Sale::getSubtotalAmount));
        BigDecimal discounts = money(sum(sales, Sale::getDiscountAmount));
        BigDecimal saleTax = money(sum(sales, Sale::getEstimatedTaxAmount));
        BigDecimal refundTotal = money(sum(refunds, Refund::getTotalAmount));
        BigDecimal refundSubtotal = money(sum(refunds, Refund::getSubtotalAmount));
        BigDecimal refundTax = money(sum(refunds, Refund::getTaxAmount));
        BigDecimal netSales = money(grossSales.subtract(discounts).subtract(refundSubtotal));
        long transactionCount = sales.size();
        BigDecimal averageTransaction = transactionCount == 0 ? moneyZero() : money(netSales.divide(BigDecimal.valueOf(transactionCount), MONEY_SCALE, RoundingMode.HALF_UP));
        BigDecimal highest = sales.stream().map(Sale::getTotalAmount).max(BigDecimal::compareTo).map(BusinessDayService::money).orElse(moneyZero());
        BigDecimal lowest = sales.stream().map(Sale::getTotalAmount).min(BigDecimal::compareTo).map(BusinessDayService::money).orElse(moneyZero());
        BigDecimal itemsSold = quantity(sales.stream().flatMap(sale -> sale.getItems().stream()).map(SaleItem::getQuantity).reduce(quantityZero(), BigDecimal::add));
        BigDecimal averageBasketSize = transactionCount == 0 ? quantityZero() : quantity(itemsSold.divide(BigDecimal.valueOf(transactionCount), QUANTITY_SCALE, RoundingMode.HALF_UP));

        List<RegisterValuesWithSession> registerValues = sessions.stream()
                .map(session -> registerValues(session, cashBreakdowns.get(session.getId()), cashMovements))
                .toList();
        BigDecimal expectedCash = money(registerValues.stream().map(value -> value.values().expectedCash()).reduce(moneyZero(), BigDecimal::add));
        BigDecimal countedCash = money(registerValues.stream().map(value -> value.values().countedCash()).reduce(moneyZero(), BigDecimal::add));
        BigDecimal variance = money(countedCash.subtract(expectedCash));
        BigDecimal voidTotal = money(sum(voidedSales, Sale::getTotalAmount));
        EndOfDayReportTotals totals = new EndOfDayReportTotals(grossSales, netSales, discounts, refundTotal, voidTotal, money(saleTax.subtract(refundTax)), transactionCount, averageTransaction, highest, lowest, itemsSold, averageBasketSize, expectedCash, countedCash, variance, day.getStore().getCurrencyCode());

        List<EndOfDayPaymentValues> paymentValues = paymentValues(sales, refunds);
        List<EndOfDayTaxValues> taxValues = taxValues(sales, refunds);
        EndOfDayLotteryValues lotteryValues = lotteryValues(lotteryEnabled, lotterySales, lotteryPayouts, cancellations, reversals, settlements);
        EndOfDayInventoryValues inventoryValues = inventoryValues(inventoryTransactions, balances);
        List<EndOfDayCashierValues> cashierValues = cashierValues(sales, refunds, lotterySales, lotteryPayouts);
        List<EndOfDayExceptionValues> exceptionValues = exceptionValues(day, sales, voidedSales, refunds, sessions, cashMovements, lotteryPayouts, reversals, variance);
        Map<String, Object> snapshot = snapshotMap(day, totals, registerValues, paymentValues, taxValues, lotteryValues, inventoryValues, cashierValues, exceptionValues);
        return new GeneratedReport(totals, registerValues, paymentValues, taxValues, lotteryValues, inventoryValues, cashierValues, exceptionValues, snapshot);
    }

    private ClosingValidationResponse validate(BusinessDay day, boolean force) {
        List<ClosingBlockerResponse> blockers = new ArrayList<>();
        if (day.getStatus() != BusinessDayStatus.OPEN && day.getStatus() != BusinessDayStatus.CLOSING && day.getStatus() != BusinessDayStatus.REOPENED) {
            blockers.add(blocker("INVALID_STATUS", "Business day must be open or closing", day.getId()));
        }
        for (RegisterSession session : registerSessions(day)) {
            if (session.getStatus() == RegisterSessionStatus.OPEN) {
                blockers.add(blocker("OPEN_REGISTER_SESSION", "Register session remains open: " + session.getRegister().getCode(), session.getId()));
            }
            if (session.getCountedCash() == null) {
                blockers.add(blocker("MISSING_COUNTED_CASH", "Counted cash is missing for register: " + session.getRegister().getCode(), session.getId()));
            }
            if (session.getExpectedCashAtClose() == null) {
                blockers.add(blocker("MISSING_RECONCILIATION", "Register reconciliation is missing for register: " + session.getRegister().getCode(), session.getId()));
            }
        }
        List<Sale> unsettledSales = saleRepository.findAll(unfinalizedSaleSpec(day.getStore().getId(), day.getBusinessDate()), Sort.by("createdAt").and(Sort.by("id")));
        unsettledSales.forEach(sale -> blockers.add(blocker("UNFINALIZED_SALE", "Sale remains in " + sale.getStatus() + " state", sale.getId())));
        List<Sale> postedSales = saleRepository.findAll(saleSpec(day.getStore().getId(), day.getBusinessDate()), Sort.by("completedAt").and(Sort.by("id")));
        postedSales.stream()
                .filter(sale -> money(sale.getPayments().stream().map(Payment::getAmount).reduce(moneyZero(), BigDecimal::add)).compareTo(money(sale.getTotalAmount())) < 0)
                .forEach(sale -> blockers.add(blocker("INCOMPLETE_PAYMENT", "Completed sale has incomplete payments", sale.getId())));
        List<Refund> postedRefunds = refundRepository.findAll(refundSpec(day.getStore().getId(), day.getBusinessDate()), Sort.by("occurredAt").and(Sort.by("id")));
        postedRefunds.stream()
                .filter(refund -> money(refund.getPayments().stream().map(RefundPayment::getAmount).reduce(moneyZero(), BigDecimal::add)).compareTo(money(refund.getTotalAmount())) < 0)
                .forEach(refund -> blockers.add(blocker("PARTIALLY_POSTED_REFUND", "Refund remains partially posted", refund.getId())));
        List<LotteryPayout> pendingPayouts = lotteryPayoutRepository.findAll(pendingLotteryPayoutSpec(day.getStore().getId(), day.getBusinessDate()), Sort.by("occurredAt").and(Sort.by("id")));
        pendingPayouts.forEach(payout -> blockers.add(blocker("PENDING_LOTTERY_PAYOUT", "Lottery payout remains unresolved", payout.getId())));
        List<LotterySettlement> pendingSettlements = lotterySettlementRepository.findAll(pendingSettlementSpec(day.getStore().getId(), day.getBusinessDate()), Sort.by("periodEnd").and(Sort.by("id")));
        pendingSettlements.forEach(settlement -> blockers.add(blocker("PENDING_LOTTERY_SETTLEMENT", "Settlement-blocking lottery operation remains pending", settlement.getId())));
        if (force) {
            return new ClosingValidationResponse(day.getId(), true, blockers);
        }
        return new ClosingValidationResponse(day.getId(), blockers.isEmpty(), blockers);
    }

    private RegisterValuesWithSession registerValues(RegisterSession session, CashLedgerBreakdownResponse breakdown, List<CashMovement> cashMovements) {
        BigDecimal safeDrops = cashMovementTotal(cashMovements, session.getId(), CashMovementType.SAFE_DROP);
        BigDecimal floatAdditions = cashMovementTotal(cashMovements, session.getId(), CashMovementType.FLOAT_ADD);
        BigDecimal floatRemovals = cashMovementTotal(cashMovements, session.getId(), CashMovementType.FLOAT_REMOVE);
        BigDecimal expenses = cashMovementTotal(cashMovements, session.getId(), CashMovementType.EXPENSE);
        BigDecimal corrections = cashMovementTotal(cashMovements, session.getId(), CashMovementType.CORRECTION);
        RegisterSummaryValues values = new RegisterSummaryValues(
                money(breakdown.openingCash()),
                money(breakdown.retailCashReceived()),
                money(breakdown.retailChange()),
                money(breakdown.retailRefunds()),
                money(breakdown.lotteryCashSales()),
                money(breakdown.lotteryPayouts()),
                money(breakdown.payoutReversals()),
                money(breakdown.lotterySaleCancellations()),
                cashMovementTotal(cashMovements, session.getId(), CashMovementType.CASH_IN),
                cashMovementTotal(cashMovements, session.getId(), CashMovementType.CASH_OUT),
                safeDrops,
                floatAdditions,
                floatRemovals,
                expenses,
                corrections,
                money(breakdown.expectedCash()),
                money(session.getCountedCash() == null ? BigDecimal.ZERO : session.getCountedCash()),
                money(session.getDifferenceCash() == null ? BigDecimal.ZERO : session.getDifferenceCash()));
        return new RegisterValuesWithSession(session, values);
    }

    private List<EndOfDayPaymentValues> paymentValues(List<Sale> sales, List<Refund> refunds) {
        Map<PaymentMethod, PaymentAccumulator> totals = new EnumMap<>(PaymentMethod.class);
        long splitPaymentCount = sales.stream().filter(sale -> sale.getPayments().size() > 1).count();
        sales.forEach(sale -> sale.getPayments().forEach(payment -> {
            PaymentAccumulator accumulator = totals.computeIfAbsent(payment.getMethod(), ignored -> new PaymentAccumulator());
            accumulator.collected = accumulator.collected.add(money(payment.getAmount()));
            accumulator.cashTendered = accumulator.cashTendered.add(money(payment.getCashTendered()));
            accumulator.changeGiven = accumulator.changeGiven.add(money(payment.getChangeDue()));
            accumulator.transactionCount++;
        }));
        refunds.forEach(refund -> refund.getPayments().forEach(payment -> {
            PaymentAccumulator accumulator = totals.computeIfAbsent(payment.getMethod(), ignored -> new PaymentAccumulator());
            accumulator.refunded = accumulator.refunded.add(money(payment.getAmount()));
            accumulator.transactionCount++;
        }));
        return totals.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new EndOfDayPaymentValues(
                        entry.getKey(),
                        money(entry.getValue().collected),
                        money(entry.getValue().refunded),
                        money(entry.getValue().collected.subtract(entry.getValue().refunded)),
                        money(entry.getValue().cashTendered),
                        money(entry.getValue().changeGiven),
                        entry.getValue().transactionCount,
                        splitPaymentCount))
                .toList();
    }

    private List<EndOfDayTaxValues> taxValues(List<Sale> sales, List<Refund> refunds) {
        Map<String, TaxAccumulator> taxes = new LinkedHashMap<>();
        TaxAccumulator salesTax = taxes.computeIfAbsent("SALES_TAX", ignored -> new TaxAccumulator("SALES_TAX", "Posted sales tax"));
        sales.forEach(sale -> {
            salesTax.taxableSales = salesTax.taxableSales.add(money(sale.getSubtotalAmount().subtract(sale.getDiscountAmount())));
            salesTax.taxCollected = salesTax.taxCollected.add(money(sale.getEstimatedTaxAmount()));
        });
        refunds.forEach(refund -> refund.getItemTaxes().forEach(tax -> {
            TaxAccumulator accumulator = taxes.computeIfAbsent(tax.getTaxComponentCode(), ignored -> new TaxAccumulator(tax.getTaxComponentCode(), tax.getTaxComponentName()));
            accumulator.taxRefunded = accumulator.taxRefunded.add(money(tax.getTaxAmount()));
        }));
        return taxes.values().stream()
                .map(total -> new EndOfDayTaxValues(total.componentCode, total.componentName, money(total.taxableSales), moneyZero(), moneyZero(), moneyZero(), money(total.taxCollected), money(total.taxRefunded), moneyZero()))
                .toList();
    }

    private EndOfDayLotteryValues lotteryValues(boolean enabled, List<LotterySale> sales, List<LotteryPayout> payouts, List<LotterySaleCancellation> cancellations, List<LotteryPayoutReversal> reversals, List<LotterySettlement> settlements) {
        if (!enabled) {
            return EndOfDayLotteryValues.empty(false);
        }
        BigDecimal salesTotal = money(sales.stream().filter(sale -> sale.getStatus() == LotterySaleStatus.RECORDED || sale.getStatus() == LotterySaleStatus.CANCELLED).map(LotterySale::getAmount).reduce(moneyZero(), BigDecimal::add));
        BigDecimal payoutsTotal = money(payouts.stream().filter(payout -> payout.getStatus() == LotteryPayoutStatus.PAID || payout.getStatus() == LotteryPayoutStatus.REVERSED).map(LotteryPayout::getAmount).reduce(moneyZero(), BigDecimal::add));
        BigDecimal cancellationTotal = money(cancellations.stream().map(LotterySaleCancellation::getAmount).reduce(moneyZero(), BigDecimal::add));
        BigDecimal reversalTotal = money(reversals.stream().map(LotteryPayoutReversal::getAmount).reduce(moneyZero(), BigDecimal::add));
        BigDecimal cashSales = money(sales.stream().filter(sale -> sale.getPaymentMethod() == PaymentMethod.CASH).map(LotterySale::getAmount).reduce(moneyZero(), BigDecimal::add));
        BigDecimal cashPayouts = money(payouts.stream().filter(payout -> payout.getPayoutMethod() == LotteryPayoutMethod.CASH).map(LotteryPayout::getAmount).reduce(moneyZero(), BigDecimal::add));
        BigDecimal commission = money(settlements.stream().map(LotterySettlement::getCommission).reduce(moneyZero(), BigDecimal::add));
        BigDecimal settlement = money(settlements.stream().map(LotterySettlement::getExpectedSettlement).reduce(moneyZero(), BigDecimal::add));
        return new EndOfDayLotteryValues(
                true,
                salesTotal,
                payoutsTotal,
                cancellationTotal,
                reversalTotal,
                money(cashSales.subtract(cashPayouts).subtract(cancellationTotal).add(reversalTotal)),
                money(salesTotal.subtract(cashSales).subtract(payoutsTotal.subtract(cashPayouts))),
                commission,
                settlement,
                payouts.stream().filter(payout -> payout.getStatus() == LotteryPayoutStatus.REFERRED_TO_OPERATOR).count(),
                payouts.stream().filter(payout -> payout.getStatus() == LotteryPayoutStatus.REFERRED_TO_OPERATOR).count(),
                payouts.stream().mapToLong(payout -> payout.getApprovals().size()).sum(),
                payouts.stream().filter(payout -> payout.getStatus() == LotteryPayoutStatus.REJECTED).count(),
                groupedTotals(sales, sale -> sale.getOperator().getCode(), LotterySale::getAmount),
                groupedTotals(sales, sale -> sale.getRegister().getCode(), LotterySale::getAmount),
                groupedTotals(sales, sale -> sale.getCashier().getEmail(), LotterySale::getAmount));
    }

    private EndOfDayInventoryValues inventoryValues(List<InventoryTransaction> transactions, List<InventoryBalance> balances) {
        BigDecimal deductedBySales = quantity(absQuantity(transactions, InventoryTransactionType.SALE));
        BigDecimal restoredByReturns = quantity(absQuantity(transactions, InventoryTransactionType.RETURN));
        BigDecimal manualIncreases = quantity(absQuantity(transactions, InventoryTransactionType.ADJUSTMENT_INCREASE));
        BigDecimal manualDecreases = quantity(absQuantity(transactions, InventoryTransactionType.ADJUSTMENT_DECREASE));
        BigDecimal damaged = quantity(absQuantity(transactions, InventoryTransactionType.DAMAGED));
        BigDecimal expired = quantity(absQuantity(transactions, InventoryTransactionType.EXPIRED));
        BigDecimal transferIn = quantity(absQuantity(transactions, InventoryTransactionType.TRANSFER_IN));
        BigDecimal transferOut = quantity(absQuantity(transactions, InventoryTransactionType.TRANSFER_OUT));
        BigDecimal stockCounts = quantity(transactions.stream()
                .filter(transaction -> transaction.getTransactionType() == InventoryTransactionType.STOCK_COUNT_INCREASE || transaction.getTransactionType() == InventoryTransactionType.STOCK_COUNT_DECREASE)
                .map(InventoryTransaction::getQuantityDelta)
                .reduce(quantityZero(), BigDecimal::add));
        BigDecimal valueMovement = money(transactions.stream()
                .map(transaction -> transaction.getQuantityDelta().multiply(transaction.getProduct().getCost()))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        return new EndOfDayInventoryValues(
                deductedBySales,
                restoredByReturns,
                manualIncreases,
                manualDecreases,
                damaged,
                expired,
                transferIn,
                transferOut,
                stockCounts,
                balances.stream().filter(balance -> balance.getQuantityOnHand().compareTo(new BigDecimal("5.0000")) <= 0 && balance.getQuantityOnHand().signum() >= 0).count(),
                balances.stream().filter(balance -> balance.getQuantityOnHand().signum() < 0).count(),
                valueMovement);
    }

    private List<EndOfDayCashierValues> cashierValues(List<Sale> sales, List<Refund> refunds, List<LotterySale> lotterySales, List<LotteryPayout> lotteryPayouts) {
        Map<UUID, CashierAccumulator> totals = new LinkedHashMap<>();
        sales.forEach(sale -> {
            CashierAccumulator total = totals.computeIfAbsent(sale.getCompletedBy().getId(), ignored -> new CashierAccumulator(sale.getCompletedBy()));
            total.transactionCount++;
            total.grossSales = total.grossSales.add(money(sale.getSubtotalAmount()));
            total.netSales = total.netSales.add(money(sale.getSubtotalAmount().subtract(sale.getDiscountAmount())));
            total.discountTotal = total.discountTotal.add(money(sale.getDiscountAmount()));
            total.priceOverrideCount += sale.getItems().stream().filter(SaleItem::isPriceOverride).count();
            total.cashHandled = total.cashHandled.add(sale.getPayments().stream().filter(payment -> payment.getMethod() == PaymentMethod.CASH).map(Payment::getAmount).reduce(moneyZero(), BigDecimal::add));
            total.registers.add(sale.getRegister().getCode());
            total.activity(sale.getCompletedAt());
        });
        refunds.forEach(refund -> {
            CashierAccumulator total = totals.computeIfAbsent(refund.getCreatedBy().getId(), ignored -> new CashierAccumulator(refund.getCreatedBy()));
            total.refundTotal = total.refundTotal.add(money(refund.getTotalAmount()));
            total.cashHandled = total.cashHandled.subtract(refund.getPayments().stream().filter(payment -> payment.getMethod() == PaymentMethod.CASH).map(RefundPayment::getAmount).reduce(moneyZero(), BigDecimal::add));
            total.registers.add(refund.getRegister().getCode());
            total.activity(refund.getOccurredAt());
        });
        lotterySales.forEach(sale -> {
            CashierAccumulator total = totals.computeIfAbsent(sale.getCashier().getId(), ignored -> new CashierAccumulator(sale.getCashier()));
            total.lotterySales = total.lotterySales.add(money(sale.getAmount()));
            total.registers.add(sale.getRegister().getCode());
            total.activity(sale.getOccurredAt());
        });
        lotteryPayouts.forEach(payout -> {
            CashierAccumulator total = totals.computeIfAbsent(payout.getCashier().getId(), ignored -> new CashierAccumulator(payout.getCashier()));
            total.lotteryPayouts = total.lotteryPayouts.add(money(payout.getAmount()));
            total.registers.add(payout.getRegister().getCode());
            total.activity(payout.getOccurredAt());
        });
        return totals.values().stream()
                .map(total -> new EndOfDayCashierValues(
                        total.cashier,
                        display(total.cashier),
                        total.transactionCount,
                        money(total.grossSales),
                        money(total.netSales.subtract(total.refundTotal)),
                        money(total.refundTotal),
                        total.voidCount,
                        money(total.discountTotal),
                        total.priceOverrideCount,
                        money(total.cashHandled),
                        money(total.lotterySales),
                        money(total.lotteryPayouts),
                        total.transactionCount == 0 ? moneyZero() : money(total.netSales.divide(BigDecimal.valueOf(total.transactionCount), MONEY_SCALE, RoundingMode.HALF_UP)),
                        total.firstActivityAt,
                        total.lastActivityAt,
                        String.join(",", total.registers)))
                .sorted(Comparator.comparing(EndOfDayCashierValues::cashierName))
                .toList();
    }

    private List<EndOfDayExceptionValues> exceptionValues(BusinessDay day, List<Sale> sales, List<Sale> voidedSales, List<Refund> refunds, List<RegisterSession> sessions, List<CashMovement> movements, List<LotteryPayout> lotteryPayouts, List<LotteryPayoutReversal> reversals, BigDecimal variance) {
        List<EndOfDayExceptionValues> values = new ArrayList<>();
        addException(values, EndOfDayExceptionType.PRICE_OVERRIDES, sales.stream().flatMap(sale -> sale.getItems().stream()).filter(SaleItem::isPriceOverride).count(), moneyZero(), null);
        addException(values, EndOfDayExceptionType.MANUAL_DISCOUNTS, sales.stream().filter(sale -> sale.getDiscountAmount().signum() > 0).count(), money(sum(sales, Sale::getDiscountAmount)), null);
        addException(values, EndOfDayExceptionType.VOIDED_TRANSACTIONS, voidedSales.size(), money(sum(voidedSales, Sale::getTotalAmount)), null);
        addException(values, EndOfDayExceptionType.REFUNDS, refunds.size(), money(sum(refunds, Refund::getTotalAmount)), null);
        addException(values, EndOfDayExceptionType.FORCE_CLOSED_SESSIONS, sessions.stream().filter(session -> session.getStatus() == RegisterSessionStatus.FORCE_CLOSED).count(), moneyZero(), null);
        addException(values, EndOfDayExceptionType.CASH_VARIANCES, variance.signum() == 0 ? 0 : 1, variance.abs(), null);
        addException(values, EndOfDayExceptionType.LOTTERY_REFERRALS, lotteryPayouts.stream().filter(payout -> payout.getStatus() == LotteryPayoutStatus.REFERRED_TO_OPERATOR).count(), moneyZero(), null);
        addException(values, EndOfDayExceptionType.LOTTERY_REVERSALS, reversals.size(), money(sum(reversals, LotteryPayoutReversal::getAmount)), null);
        addException(values, EndOfDayExceptionType.MANUAL_CASH_CORRECTIONS, movements.stream().filter(movement -> movement.getType() == CashMovementType.CORRECTION).count(), cashMovementTotal(movements, null, CashMovementType.CORRECTION), null);
        addException(values, EndOfDayExceptionType.REOPENED_BUSINESS_DAY, day.getStatus() == BusinessDayStatus.REOPENED || day.getReopenReason() != null ? 1 : 0, moneyZero(), day.getReopenReason());
        return values;
    }

    private List<RegisterSession> registerSessions(BusinessDay day) {
        return registerSessionRepository.findAll(registerSessionSpec(day), Sort.by("openedAt").and(Sort.by("id")));
    }

    private void validateSignOff(BusinessDayConfiguration configuration, BigDecimal cashVariance, String notes, String varianceExplanation, Boolean confirmationAccepted) {
        if (configuration.isRequireManagerSignOff() && !Boolean.TRUE.equals(confirmationAccepted)) {
            throw new BadRequestException("confirmationAccepted must be true");
        }
        if (cashVariance.abs().compareTo(configuration.getCashVarianceExplanationThreshold()) > 0 && cleanOptional(varianceExplanation) == null) {
            throw new BadRequestException("varianceExplanation is required when cash variance exceeds the configured threshold");
        }
    }

    private BusinessDay saveDay(BusinessDay day) {
        try {
            return businessDayRepository.saveAndFlush(day);
        } catch (ObjectOptimisticLockingFailureException | OptimisticLockException exception) {
            throw new ConflictException("Business day was modified by another transaction");
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Business day conflicts with an existing active day or report");
        }
    }

    private Store store(UUID storeId) {
        if (storeId == null) {
            throw new BadRequestException("storeId is required");
        }
        return storeRepository.findById(storeId).orElseThrow(() -> new NotFoundException("Store not found"));
    }

    private BusinessDay day(UUID id) {
        if (id == null) {
            throw new BadRequestException("id is required");
        }
        return businessDayRepository.findById(id).orElseThrow(() -> new NotFoundException("Business day not found"));
    }

    private BusinessDay dayForUpdate(UUID id) {
        if (id == null) {
            throw new BadRequestException("id is required");
        }
        return businessDayRepository.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Business day not found"));
    }

    private EndOfDayReport report(UUID id) {
        if (id == null) {
            throw new BadRequestException("id is required");
        }
        return reportRepository.findById(id).orElseThrow(() -> new NotFoundException("End-of-day report not found"));
    }

    UUID currentUserId(Authentication authentication) {
        return currentUser(authentication).getId();
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new BadRequestException("Authenticated user is required");
        }
        return userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new BadRequestException("Authenticated user is required"));
    }

    private BusinessDayConfiguration configuration(Store store) {
        return configurationRepository.findByStore_Id(store.getId()).orElseGet(() -> BusinessDayConfiguration.defaults(store));
    }

    private void audit(User actor, AuditAction action, BusinessDay day, Object before, Object after, String reason) {
        auditService.record(new CreateAuditRecordCommand(actor.getId(), action, "BUSINESS_DAY", day.getId(), day.getStore().getId(), null, before, after, reason));
    }

    private void requireStoreManagement(Authentication authentication, UUID storeId) {
        if (storeAccessService != null) {
            storeAccessService.requireStoreManagement(authentication, storeId);
        }
    }

    private void requireStoreAccess(Authentication authentication, UUID storeId) {
        if (storeAccessService != null) {
            storeAccessService.requireStoreAccess(authentication, storeId);
        }
    }

    private String snapshot(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("End-of-day snapshot must be JSON serializable", exception);
        }
    }

    private static Map<String, Object> snapshotMap(BusinessDay day, EndOfDayReportTotals totals, List<RegisterValuesWithSession> registers, List<EndOfDayPaymentValues> payments, List<EndOfDayTaxValues> taxes, EndOfDayLotteryValues lottery, EndOfDayInventoryValues inventory, List<EndOfDayCashierValues> cashiers, List<EndOfDayExceptionValues> exceptions) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("businessDayId", day.getId());
        snapshot.put("storeId", day.getStore().getId());
        snapshot.put("businessDate", day.getBusinessDate());
        snapshot.put("generatedFromStatus", day.getStatus());
        snapshot.put("totals", totals);
        snapshot.put("registers", registers.stream().map(RegisterValuesWithSession::values).toList());
        snapshot.put("payments", payments);
        snapshot.put("taxes", taxes);
        snapshot.put("lottery", lottery);
        snapshot.put("inventory", inventory);
        snapshot.put("cashiers", cashiers);
        snapshot.put("exceptions", exceptions);
        return snapshot;
    }

    private static ClosingBlockerResponse blocker(String code, String message, UUID relatedId) {
        return new ClosingBlockerResponse(code, message, relatedId);
    }

    private static void requireVersion(BusinessDay day, Long version) {
        if (version == null) {
            throw new BadRequestException("version is required");
        }
        if (day.getVersion() != version) {
            throw new ConflictException("Business day was modified by another transaction");
        }
    }

    private static void addException(List<EndOfDayExceptionValues> values, EndOfDayExceptionType type, long count, BigDecimal amount, String details) {
        if (count > 0 || amount.signum() != 0 || details != null) {
            values.add(new EndOfDayExceptionValues(type, count, amount, details));
        }
    }

    private static BigDecimal cashMovementTotal(List<CashMovement> movements, UUID sessionId, CashMovementType type) {
        return money(movements.stream()
                .filter(movement -> sessionId == null || movement.getRegisterSession().getId().equals(sessionId))
                .filter(movement -> movement.getType() == type)
                .map(movement -> movement.getDirection() == CashLedgerDirection.OUT ? movement.getAmount().negate() : movement.getAmount())
                .reduce(moneyZero(), BigDecimal::add));
    }

    private static BigDecimal absQuantity(List<InventoryTransaction> transactions, InventoryTransactionType type) {
        return transactions.stream()
                .filter(transaction -> transaction.getTransactionType() == type)
                .map(transaction -> transaction.getQuantityDelta().abs())
                .reduce(quantityZero(), BigDecimal::add);
    }

    private static <T> BigDecimal sum(List<T> values, Function<T, BigDecimal> selector) {
        return values.stream()
                .map(selector)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static <T> String groupedTotals(List<T> rows, Function<T, String> key, Function<T, BigDecimal> amount) {
        return rows.stream()
                .collect(Collectors.groupingBy(key, LinkedHashMap::new, Collectors.mapping(amount, Collectors.reducing(moneyZero(), BigDecimal::add))))
                .entrySet()
                .stream()
                .map(entry -> entry.getKey() + "=" + money(entry.getValue()))
                .collect(Collectors.joining(","));
    }

    private static String display(User user) {
        return user.getDisplayName() == null || user.getDisplayName().isBlank() ? user.getEmail() : user.getDisplayName();
    }

    private static String cleanRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(fieldName + " is required");
        }
        return value.trim();
    }

    private static String cleanOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static BigDecimal money(BigDecimal value) {
        if (value == null) {
            return moneyZero();
        }
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal moneyZero() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal quantity(BigDecimal value) {
        if (value == null) {
            return quantityZero();
        }
        return value.setScale(QUANTITY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal quantityZero() {
        return BigDecimal.ZERO.setScale(QUANTITY_SCALE, RoundingMode.HALF_UP);
    }

    private static Specification<BusinessDay> equalReference(String field, UUID value) {
        if (value == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get(field).get("id"), value);
    }

    private static Specification<BusinessDay> equalEnum(String field, Enum<?> value) {
        if (value == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get(field), value);
    }

    private static Specification<BusinessDay> dateGreaterThanOrEqualTo(LocalDate value) {
        if (value == null) {
            return null;
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("businessDate"), value);
    }

    private static Specification<BusinessDay> dateLessThanOrEqualTo(LocalDate value) {
        if (value == null) {
            return null;
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("businessDate"), value);
    }

    private static Specification<EndOfDayReport> reportStore(UUID value) {
        if (value == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("store").get("id"), value);
    }

    private static Specification<EndOfDayReport> reportDateGreaterThanOrEqualTo(LocalDate value) {
        if (value == null) {
            return null;
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("businessDate"), value);
    }

    private static Specification<EndOfDayReport> reportDateLessThanOrEqualTo(LocalDate value) {
        if (value == null) {
            return null;
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("businessDate"), value);
    }

    private static Specification<EndOfDayReport> reportBusinessDayStatus(BusinessDayStatus value) {
        if (value == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("businessDay").get("status"), value);
    }

    private static Specification<EndOfDayReport> reportClosedBy(UUID value) {
        if (value == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("businessDay").get("closedBy").get("id"), value);
    }

    private static Specification<EndOfDayReport> reportNumber(String value) {
        String cleaned = cleanOptional(value);
        if (cleaned == null) {
            return null;
        }
        return (root, query, cb) -> cb.like(cb.lower(root.get("reportNumber")), "%" + cleaned.toLowerCase(Locale.ROOT) + "%");
    }

    private static Specification<Sale> saleSpec(UUID storeId, LocalDate businessDate) {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("store").get("id"), storeId),
                cb.equal(root.get("businessDate"), businessDate),
                root.get("status").in(FINANCIALLY_POSTED_SALE_STATUSES));
    }

    private static Specification<Sale> unfinalizedSaleSpec(UUID storeId, LocalDate businessDate) {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("store").get("id"), storeId),
                cb.equal(root.get("businessDate"), businessDate),
                root.get("status").in(List.of(SaleStatus.DRAFT, SaleStatus.HELD)));
    }

    private static Specification<Sale> voidedSaleSpec(UUID storeId, LocalDate businessDate) {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("store").get("id"), storeId),
                cb.equal(root.get("businessDate"), businessDate),
                cb.equal(root.get("status"), SaleStatus.VOIDED));
    }

    private static Specification<Refund> refundSpec(UUID storeId, LocalDate businessDate) {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("store").get("id"), storeId),
                cb.equal(root.get("businessDate"), businessDate));
    }

    private static Specification<RegisterSession> registerSessionSpec(BusinessDay day) {
        Instant start = day.getBusinessDate().atStartOfDay().atZone(ZoneId.of(day.getTimezone())).toInstant();
        Instant end = day.getBusinessDate().plusDays(1).atStartOfDay().atZone(ZoneId.of(day.getTimezone())).toInstant();
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("store").get("id"), day.getStore().getId()),
                cb.or(
                        cb.equal(root.get("businessDay").get("id"), day.getId()),
                        cb.and(
                                cb.isNull(root.get("businessDay")),
                                cb.greaterThanOrEqualTo(root.get("openedAt"), start),
                                cb.lessThan(root.get("openedAt"), end))));
    }

    private static Specification<CashMovement> cashMovementSpec(UUID storeId, LocalDate businessDate, String timezone) {
        Instant start = businessDate.atStartOfDay().atZone(ZoneId.of(timezone)).toInstant();
        Instant end = businessDate.plusDays(1).atStartOfDay().atZone(ZoneId.of(timezone)).toInstant();
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("store").get("id"), storeId),
                cb.greaterThanOrEqualTo(root.get("occurredAt"), start),
                cb.lessThan(root.get("occurredAt"), end));
    }

    private static Specification<InventoryTransaction> inventoryTransactionSpec(UUID storeId, LocalDate businessDate, String timezone) {
        Instant start = businessDate.atStartOfDay().atZone(ZoneId.of(timezone)).toInstant();
        Instant end = businessDate.plusDays(1).atStartOfDay().atZone(ZoneId.of(timezone)).toInstant();
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("store").get("id"), storeId),
                cb.greaterThanOrEqualTo(root.get("occurredAt"), start),
                cb.lessThan(root.get("occurredAt"), end));
    }

    private static Specification<InventoryBalance> balanceSpec(UUID storeId) {
        return (root, query, cb) -> cb.equal(root.get("store").get("id"), storeId);
    }

    private static Specification<LotterySale> lotterySaleSpec(UUID storeId, LocalDate businessDate, String timezone) {
        Instant start = businessDate.atStartOfDay().atZone(ZoneId.of(timezone)).toInstant();
        Instant end = businessDate.plusDays(1).atStartOfDay().atZone(ZoneId.of(timezone)).toInstant();
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("store").get("id"), storeId),
                cb.greaterThanOrEqualTo(root.get("occurredAt"), start),
                cb.lessThan(root.get("occurredAt"), end));
    }

    private static Specification<LotteryPayout> lotteryPayoutSpec(UUID storeId, LocalDate businessDate) {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("store").get("id"), storeId),
                cb.equal(root.get("businessDate"), businessDate));
    }

    private static Specification<LotteryPayout> pendingLotteryPayoutSpec(UUID storeId, LocalDate businessDate) {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("store").get("id"), storeId),
                cb.equal(root.get("businessDate"), businessDate),
                root.get("status").in(List.of(LotteryPayoutStatus.DRAFT, LotteryPayoutStatus.VALIDATED, LotteryPayoutStatus.AUTHORIZED, LotteryPayoutStatus.REFERRED_TO_OPERATOR)));
    }

    private static Specification<LotterySaleCancellation> lotteryCancellationSpec(UUID storeId, LocalDate businessDate, String timezone) {
        Instant start = businessDate.atStartOfDay().atZone(ZoneId.of(timezone)).toInstant();
        Instant end = businessDate.plusDays(1).atStartOfDay().atZone(ZoneId.of(timezone)).toInstant();
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("originalSale").get("store").get("id"), storeId),
                cb.greaterThanOrEqualTo(root.get("cancelledAt"), start),
                cb.lessThan(root.get("cancelledAt"), end));
    }

    private static Specification<LotteryPayoutReversal> lotteryReversalSpec(UUID storeId, LocalDate businessDate, String timezone) {
        Instant start = businessDate.atStartOfDay().atZone(ZoneId.of(timezone)).toInstant();
        Instant end = businessDate.plusDays(1).atStartOfDay().atZone(ZoneId.of(timezone)).toInstant();
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("originalPayout").get("store").get("id"), storeId),
                cb.greaterThanOrEqualTo(root.get("reversedAt"), start),
                cb.lessThan(root.get("reversedAt"), end));
    }

    private static Specification<LotterySettlement> lotterySettlementSpec(UUID storeId, LocalDate businessDate) {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("store").get("id"), storeId),
                cb.lessThanOrEqualTo(root.get("periodStart"), businessDate),
                cb.greaterThanOrEqualTo(root.get("periodEnd"), businessDate));
    }

    private static Specification<LotterySettlement> pendingSettlementSpec(UUID storeId, LocalDate businessDate) {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("store").get("id"), storeId),
                cb.lessThanOrEqualTo(root.get("periodStart"), businessDate),
                cb.greaterThanOrEqualTo(root.get("periodEnd"), businessDate),
                root.get("status").in(List.of(LotterySettlementStatus.CALCULATED, LotterySettlementStatus.APPROVED, LotterySettlementStatus.REOPENED)));
    }

    private static final class PaymentAccumulator {
        private BigDecimal collected = moneyZero();
        private BigDecimal refunded = moneyZero();
        private BigDecimal cashTendered = moneyZero();
        private BigDecimal changeGiven = moneyZero();
        private long transactionCount;
    }

    private static final class TaxAccumulator {
        private final String componentCode;
        private final String componentName;
        private BigDecimal taxableSales = moneyZero();
        private BigDecimal taxCollected = moneyZero();
        private BigDecimal taxRefunded = moneyZero();

        private TaxAccumulator(String componentCode, String componentName) {
            this.componentCode = componentCode;
            this.componentName = componentName;
        }
    }

    private static final class CashierAccumulator {
        private final User cashier;
        private BigDecimal grossSales = moneyZero();
        private BigDecimal netSales = moneyZero();
        private BigDecimal refundTotal = moneyZero();
        private BigDecimal discountTotal = moneyZero();
        private BigDecimal cashHandled = moneyZero();
        private BigDecimal lotterySales = moneyZero();
        private BigDecimal lotteryPayouts = moneyZero();
        private long transactionCount;
        private long voidCount;
        private long priceOverrideCount;
        private Instant firstActivityAt;
        private Instant lastActivityAt;
        private final Set<String> registers = new LinkedHashSet<>();

        private CashierAccumulator(User cashier) {
            this.cashier = cashier;
        }

        private void activity(Instant occurredAt) {
            if (occurredAt == null) {
                return;
            }
            if (firstActivityAt == null || occurredAt.isBefore(firstActivityAt)) {
                firstActivityAt = occurredAt;
            }
            if (lastActivityAt == null || occurredAt.isAfter(lastActivityAt)) {
                lastActivityAt = occurredAt;
            }
        }
    }

    private record RegisterValuesWithSession(RegisterSession session, RegisterSummaryValues values) {
    }

    private record EndOfDayPaymentValues(PaymentMethod method, BigDecimal collected, BigDecimal refunded, BigDecimal net, BigDecimal cashTendered, BigDecimal changeGiven, long transactionCount, long splitPaymentCount) {
    }

    private record EndOfDayTaxValues(String componentCode, String componentName, BigDecimal taxableSales, BigDecimal exemptSales, BigDecimal zeroRatedSales, BigDecimal outOfScopeSales, BigDecimal taxCollected, BigDecimal taxRefunded, BigDecimal roundingAdjustment) {
    }

    private record EndOfDayLotteryValues(boolean enabled, BigDecimal lotterySales, BigDecimal lotteryPayouts, BigDecimal saleCancellations, BigDecimal payoutReversals, BigDecimal cashLotteryActivity, BigDecimal nonCashLotteryActivity, BigDecimal commissionEarned, BigDecimal settlementAmount, long operatorReferrals, long pendingReferrals, long approvalCount, long rejectedPayouts, String operatorTotals, String registerTotals, String cashierTotals) {
        static EndOfDayLotteryValues empty(boolean enabled) {
            return new EndOfDayLotteryValues(enabled, moneyZero(), moneyZero(), moneyZero(), moneyZero(), moneyZero(), moneyZero(), moneyZero(), moneyZero(), 0, 0, 0, 0, "", "", "");
        }
    }

    private record EndOfDayInventoryValues(BigDecimal deductedBySales, BigDecimal restoredByReturns, BigDecimal manualIncreases, BigDecimal manualDecreases, BigDecimal damagedQuantity, BigDecimal expiredQuantity, BigDecimal transferIn, BigDecimal transferOut, BigDecimal stockCountVariances, long lowStockProducts, long negativeStockProducts, BigDecimal inventoryValueMovement) {
    }

    private record EndOfDayCashierValues(User cashier, String cashierName, long transactionCount, BigDecimal grossSales, BigDecimal netSales, BigDecimal refundTotal, long voidCount, BigDecimal discountTotal, long priceOverrideCount, BigDecimal cashHandled, BigDecimal lotterySales, BigDecimal lotteryPayouts, BigDecimal averageTransactionValue, Instant firstActivityAt, Instant lastActivityAt, String registersUsed) {
    }

    private record EndOfDayExceptionValues(EndOfDayExceptionType type, long count, BigDecimal totalAmount, String details) {
    }

    private record GeneratedReport(EndOfDayReportTotals totals, List<RegisterValuesWithSession> registers, List<EndOfDayPaymentValues> payments, List<EndOfDayTaxValues> taxes, EndOfDayLotteryValues lottery, EndOfDayInventoryValues inventory, List<EndOfDayCashierValues> cashiers, List<EndOfDayExceptionValues> exceptions, Map<String, Object> snapshot) {
    }

    private static String printableHtml(EndOfDayReportResponse report) {
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html><head><meta charset=\"utf-8\"><title>")
                .append(escape(report.reportNumber()))
                .append("</title><style>")
                .append("@page{size:auto;margin:14mm}body{font-family:Arial,sans-serif;color:#111;margin:0}h1,h2{margin:0 0 8px}h1{font-size:24px}h2{font-size:16px;margin-top:20px;border-bottom:1px solid #999;padding-bottom:4px}table{width:100%;border-collapse:collapse;margin:8px 0 14px;font-size:12px}th,td{border:1px solid #ccc;padding:6px;text-align:left;vertical-align:top}th{background:#f4f4f4}.meta{display:grid;grid-template-columns:repeat(2,1fr);gap:4px 20px;font-size:13px;margin-bottom:14px}.footer{position:fixed;bottom:0;left:0;right:0;font-size:10px;color:#555;border-top:1px solid #ccc;padding-top:4px}.page-break{break-inside:avoid}@media screen{body{padding:24px;max-width:1040px;margin:auto}.footer{position:static;margin-top:24px}}")
                .append("</style></head><body>")
                .append("<h1>Merchtyl End-of-Day Report</h1><div class=\"meta\">")
                .append(meta("Store", report.storeCode() + " - " + report.storeName()))
                .append(meta("Business date", report.businessDate().toString()))
                .append(meta("Report number", report.reportNumber()))
                .append(meta("Revision", String.valueOf(report.revision())))
                .append(meta("Generated", String.valueOf(report.generatedAt())))
                .append(meta("Generated by", report.generatedByName()))
                .append("</div>");
        html.append(section("Summary", rows(
                row("Gross sales", report.grossSales()),
                row("Net sales", report.netSales()),
                row("Discounts", report.discountTotal()),
                row("Refunds", report.refundTotal()),
                row("Tax", report.taxTotal()),
                row("Expected cash", report.expectedCash()),
                row("Counted cash", report.countedCash()),
                row("Cash variance", report.cashVariance()))));
        html.append(section("Payments", table(List.of("Method", "Collected", "Refunded", "Net", "Cash tendered", "Change"),
                report.payments().stream().map(payment -> List.of(payment.paymentMethod().name(), fmt(payment.collected()), fmt(payment.refunded()), fmt(payment.net()), fmt(payment.cashTendered()), fmt(payment.changeGiven()))).toList())));
        html.append(section("Registers", table(List.of("Register", "Expected", "Counted", "Variance", "Opened", "Closed", "Force close"),
                report.registers().stream().map(register -> List.of(register.registerCode(), fmt(register.expectedCash()), fmt(register.countedCash()), fmt(register.variance()), String.valueOf(register.openedAt()), String.valueOf(register.closedAt()), register.forceClosed() ? "Yes" : "No")).toList())));
        html.append(section("Taxes", table(List.of("Component", "Taxable", "Collected", "Refunded", "Net"),
                report.taxes().stream().map(tax -> List.of(tax.componentCode(), fmt(tax.taxableSales()), fmt(tax.taxCollected()), fmt(tax.taxRefunded()), fmt(tax.netTaxCollected()))).toList())));
        html.append(section("Cashiers", table(List.of("Cashier", "Transactions", "Net sales", "Refunds", "Cash handled", "Registers"),
                report.cashiers().stream().map(cashier -> List.of(cashier.cashierName(), String.valueOf(cashier.transactionCount()), fmt(cashier.netSales()), fmt(cashier.refundTotal()), fmt(cashier.cashHandled()), cashier.registersUsed())).toList())));
        html.append(section("Exceptions", table(List.of("Type", "Count", "Amount", "Details"),
                report.exceptions().stream().map(exception -> List.of(exception.exceptionType().name(), String.valueOf(exception.count()), fmt(exception.totalAmount()), nullToEmpty(exception.details()))).toList())));
        if (report.lottery() != null) {
            html.append(section("Lottery", rows(
                    row("Enabled", report.lottery().enabled() ? "Yes" : "No"),
                    row("Sales", report.lottery().lotterySales()),
                    row("Payouts", report.lottery().lotteryPayouts()),
                    row("Settlement", report.lottery().settlementAmount()))));
        }
        if (report.inventory() != null) {
            html.append(section("Inventory", rows(
                    row("Deducted by sales", report.inventory().deductedBySales()),
                    row("Restored by returns", report.inventory().restoredByReturns()),
                    row("Manual increases", report.inventory().manualIncreases()),
                    row("Manual decreases", report.inventory().manualDecreases()),
                    row("Negative-stock products", String.valueOf(report.inventory().negativeStockProducts())),
                    row("Inventory value movement", report.inventory().inventoryValueMovement()))));
        }
        if (report.signOff() != null) {
            html.append(section("Manager Sign-off", rows(
                    row("Manager", report.signOff().managerName()),
                    row("Signed at", String.valueOf(report.signOff().signedAt())),
                    row("Notes", nullToEmpty(report.signOff().notes())),
                    row("Variance explanation", nullToEmpty(report.signOff().varianceExplanation())))));
        }
        html.append("<div class=\"footer\">Confidential Merchtyl report - ")
                .append(escape(report.reportNumber()))
                .append(" - Generated ")
                .append(escape(String.valueOf(report.generatedAt())))
                .append("</div></body></html>");
        return html.toString();
    }

    private static String csv(EndOfDayReportResponse report) {
        StringBuilder csv = new StringBuilder();
        appendCsvSection(csv, "summary", List.of("metric", "value"), List.of(
                List.of("reportNumber", report.reportNumber()),
                List.of("store", report.storeCode() + " - " + report.storeName()),
                List.of("businessDate", report.businessDate().toString()),
                List.of("grossSales", fmt(report.grossSales())),
                List.of("netSales", fmt(report.netSales())),
                List.of("refundTotal", fmt(report.refundTotal())),
                List.of("taxTotal", fmt(report.taxTotal())),
                List.of("expectedCash", fmt(report.expectedCash())),
                List.of("countedCash", fmt(report.countedCash())),
                List.of("cashVariance", fmt(report.cashVariance()))));
        appendCsvSection(csv, "payments", List.of("method", "collected", "refunded", "net", "cashTendered", "changeGiven"),
                report.payments().stream().map(payment -> List.of(payment.paymentMethod().name(), fmt(payment.collected()), fmt(payment.refunded()), fmt(payment.net()), fmt(payment.cashTendered()), fmt(payment.changeGiven()))).toList());
        appendCsvSection(csv, "registers", List.of("register", "openingFloat", "expectedCash", "countedCash", "variance", "forceClosed", "forceCloseReason"),
                report.registers().stream().map(register -> List.of(register.registerCode(), fmt(register.openingFloat()), fmt(register.expectedCash()), fmt(register.countedCash()), fmt(register.variance()), String.valueOf(register.forceClosed()), nullToEmpty(register.forceCloseReason()))).toList());
        appendCsvSection(csv, "taxes", List.of("componentCode", "componentName", "taxableSales", "taxCollected", "taxRefunded", "netTaxCollected"),
                report.taxes().stream().map(tax -> List.of(tax.componentCode(), tax.componentName(), fmt(tax.taxableSales()), fmt(tax.taxCollected()), fmt(tax.taxRefunded()), fmt(tax.netTaxCollected()))).toList());
        if (report.lottery() != null) {
            appendCsvSection(csv, "lottery", List.of("metric", "value"), List.of(
                    List.of("enabled", String.valueOf(report.lottery().enabled())),
                    List.of("lotterySales", fmt(report.lottery().lotterySales())),
                    List.of("lotteryPayouts", fmt(report.lottery().lotteryPayouts())),
                    List.of("saleCancellations", fmt(report.lottery().saleCancellations())),
                    List.of("payoutReversals", fmt(report.lottery().payoutReversals())),
                    List.of("settlementAmount", fmt(report.lottery().settlementAmount()))));
        }
        if (report.inventory() != null) {
            appendCsvSection(csv, "inventory", List.of("metric", "value"), List.of(
                    List.of("deductedBySales", fmt(report.inventory().deductedBySales())),
                    List.of("restoredByReturns", fmt(report.inventory().restoredByReturns())),
                    List.of("manualIncreases", fmt(report.inventory().manualIncreases())),
                    List.of("manualDecreases", fmt(report.inventory().manualDecreases())),
                    List.of("negativeStockProducts", String.valueOf(report.inventory().negativeStockProducts())),
                    List.of("inventoryValueMovement", fmt(report.inventory().inventoryValueMovement()))));
        }
        appendCsvSection(csv, "cashiers", List.of("cashier", "transactionCount", "grossSales", "netSales", "refundTotal", "cashHandled", "registersUsed"),
                report.cashiers().stream().map(cashier -> List.of(cashier.cashierName(), String.valueOf(cashier.transactionCount()), fmt(cashier.grossSales()), fmt(cashier.netSales()), fmt(cashier.refundTotal()), fmt(cashier.cashHandled()), cashier.registersUsed())).toList());
        appendCsvSection(csv, "exceptions", List.of("type", "count", "totalAmount", "details"),
                report.exceptions().stream().map(exception -> List.of(exception.exceptionType().name(), String.valueOf(exception.count()), fmt(exception.totalAmount()), nullToEmpty(exception.details()))).toList());
        return csv.toString();
    }

    private static List<String> pdfLines(EndOfDayReportResponse report) {
        List<String> lines = new ArrayList<>();
        lines.add("Merchtyl End-of-Day Report");
        lines.add("Store: " + report.storeCode() + " - " + report.storeName());
        lines.add("Business date: " + report.businessDate());
        lines.add("Report number: " + report.reportNumber() + " revision " + report.revision());
        lines.add("Generated: " + report.generatedAt() + " by " + report.generatedByName());
        lines.add("Gross sales: " + fmt(report.grossSales()) + " Net sales: " + fmt(report.netSales()) + " Tax: " + fmt(report.taxTotal()));
        lines.add("Expected cash: " + fmt(report.expectedCash()) + " Counted cash: " + fmt(report.countedCash()) + " Variance: " + fmt(report.cashVariance()));
        lines.add("Payments");
        report.payments().forEach(payment -> lines.add("  " + payment.paymentMethod() + " collected " + fmt(payment.collected()) + " refunded " + fmt(payment.refunded()) + " net " + fmt(payment.net())));
        lines.add("Registers");
        report.registers().forEach(register -> lines.add("  " + register.registerCode() + " expected " + fmt(register.expectedCash()) + " counted " + fmt(register.countedCash()) + " variance " + fmt(register.variance())));
        lines.add("Taxes");
        report.taxes().forEach(tax -> lines.add("  " + tax.componentCode() + " collected " + fmt(tax.taxCollected()) + " refunded " + fmt(tax.taxRefunded()) + " net " + fmt(tax.netTaxCollected())));
        if (report.lottery() != null) {
            lines.add("Lottery sales: " + fmt(report.lottery().lotterySales()) + " payouts: " + fmt(report.lottery().lotteryPayouts()) + " settlement: " + fmt(report.lottery().settlementAmount()));
        }
        if (report.inventory() != null) {
            lines.add("Inventory deducted: " + fmt(report.inventory().deductedBySales()) + " restored: " + fmt(report.inventory().restoredByReturns()) + " value movement: " + fmt(report.inventory().inventoryValueMovement()));
        }
        lines.add("Cashiers");
        report.cashiers().forEach(cashier -> lines.add("  " + cashier.cashierName() + " tx " + cashier.transactionCount() + " net " + fmt(cashier.netSales()) + " cash " + fmt(cashier.cashHandled())));
        lines.add("Exceptions");
        report.exceptions().forEach(exception -> lines.add("  " + exception.exceptionType() + " count " + exception.count() + " amount " + fmt(exception.totalAmount())));
        if (report.signOff() != null) {
            lines.add("Signed by: " + report.signOff().managerName() + " at " + report.signOff().signedAt());
            lines.add("Variance explanation: " + nullToEmpty(report.signOff().varianceExplanation()));
        }
        lines.add("Confidential Merchtyl report - " + report.reportNumber());
        return lines;
    }

    private static byte[] simplePdf(List<String> lines) {
        int linesPerPage = 44;
        List<List<String>> pages = new ArrayList<>();
        for (int index = 0; index < lines.size(); index += linesPerPage) {
            pages.add(lines.subList(index, Math.min(lines.size(), index + linesPerPage)));
        }
        if (pages.isEmpty()) {
            pages.add(List.of(""));
        }

        int pageCount = pages.size();
        int fontObjectNumber = 3 + pageCount;
        int firstContentObjectNumber = fontObjectNumber + 1;
        String kids = java.util.stream.IntStream.range(0, pageCount)
                .mapToObj(index -> (3 + index) + " 0 R")
                .collect(Collectors.joining(" "));
        List<String> objects = new ArrayList<>();
        objects.add("<< /Type /Catalog /Pages 2 0 R >>");
        objects.add("<< /Type /Pages /Kids [" + kids + "] /Count " + pageCount + " >>");
        for (int index = 0; index < pageCount; index++) {
            objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 " + fontObjectNumber + " 0 R >> >> /Contents " + (firstContentObjectNumber + index) + " 0 R >>");
        }
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");
        for (int index = 0; index < pageCount; index++) {
            String content = pdfPageContent(pages.get(index), index + 1, pageCount);
            int length = content.getBytes(StandardCharsets.UTF_8).length;
            objects.add("<< /Length " + length + " >>\nstream\n" + content + "endstream");
        }
        StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(pdf.toString().getBytes(StandardCharsets.UTF_8).length);
            pdf.append(i + 1).append(" 0 obj\n").append(objects.get(i)).append("\nendobj\n");
        }
        int xref = pdf.toString().getBytes(StandardCharsets.UTF_8).length;
        pdf.append("xref\n0 ").append(objects.size() + 1).append("\n0000000000 65535 f \n");
        offsets.forEach(offset -> pdf.append("%010d 00000 n \n".formatted(offset)));
        pdf.append("trailer << /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        return pdf.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String pdfPageContent(List<String> lines, int pageNumber, int pageCount) {
        StringBuilder content = new StringBuilder("BT\n/F1 10 Tf\n50 760 Td\n14 TL\n");
        for (String line : lines) {
            content.append("(").append(pdfEscape(line)).append(") Tj\nT*\n");
        }
        content.append("ET\nBT\n/F1 9 Tf\n50 36 Td\n")
                .append("(Page ")
                .append(pageNumber)
                .append(" of ")
                .append(pageCount)
                .append(") Tj\nET\n");
        return content.toString();
    }

    private static void appendCsvSection(StringBuilder csv, String section, List<String> headers, List<List<String>> rows) {
        csv.append("# ").append(section).append('\n');
        csv.append(headers.stream().map(BusinessDayService::csvEscape).collect(Collectors.joining(","))).append('\n');
        rows.forEach(row -> csv.append(row.stream().map(BusinessDayService::csvEscape).collect(Collectors.joining(","))).append('\n'));
        csv.append('\n');
    }

    private static String section(String title, String body) {
        return "<section class=\"page-break\"><h2>" + escape(title) + "</h2>" + body + "</section>";
    }

    private static String rows(String... rows) {
        return "<table><tbody>" + String.join("", rows) + "</tbody></table>";
    }

    private static String row(String label, BigDecimal value) {
        return row(label, fmt(value));
    }

    private static String row(String label, String value) {
        return "<tr><th>" + escape(label) + "</th><td>" + escape(value) + "</td></tr>";
    }

    private static String table(List<String> headers, List<List<String>> rows) {
        String thead = headers.stream().map(header -> "<th>" + escape(header) + "</th>").collect(Collectors.joining("", "<thead><tr>", "</tr></thead>"));
        String tbody = rows.stream()
                .map(row -> row.stream().map(cell -> "<td>" + escape(cell) + "</td>").collect(Collectors.joining("", "<tr>", "</tr>")))
                .collect(Collectors.joining("", "<tbody>", "</tbody>"));
        return "<table>" + thead + tbody + "</table>";
    }

    private static String meta(String label, String value) {
        return "<div><strong>" + escape(label) + ":</strong> " + escape(value) + "</div>";
    }

    private static String fmt(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String escape(String value) {
        return nullToEmpty(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String csvEscape(String value) {
        String cleaned = nullToEmpty(value);
        if (cleaned.contains(",") || cleaned.contains("\"") || cleaned.contains("\n")) {
            return "\"" + cleaned.replace("\"", "\"\"") + "\"";
        }
        return cleaned;
    }

    private static String pdfEscape(String value) {
        return nullToEmpty(value)
                .replace("\\", "\\\\")
                .replace("(", "\\(")
                .replace(")", "\\)");
    }
}
