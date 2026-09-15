package com.merchtyl.lottery;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.cash.*;
import com.merchtyl.common.ConflictException;
import com.merchtyl.common.ForbiddenOperationException;
import com.merchtyl.common.NotFoundException;
import com.merchtyl.features.FeatureCode;
import com.merchtyl.features.FeatureService;
import com.merchtyl.register.RegisterType;
import com.merchtyl.registersession.RegisterSession;
import com.merchtyl.registersession.RegisterSessionRepository;
import com.merchtyl.registersession.RegisterSessionStatus;
import com.merchtyl.security.StoreAccessService;
import com.merchtyl.security.User;
import org.springframework.security.core.Authentication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

@Service
public class LotteryPosActivityService {
    private final LotteryPosActivityRepository repository;
    private final RegisterSessionRepository sessions;
    private final StoreAccessService storeAccess;
    private final FeatureService features;
    private final CashLedgerService cashLedger;
    private final AuditService audit;
    private final Clock clock;

    @Autowired
    public LotteryPosActivityService(LotteryPosActivityRepository repository, RegisterSessionRepository sessions,
                                     StoreAccessService storeAccess, FeatureService features,
                                     CashLedgerService cashLedger, AuditService audit) {
        this(repository, sessions, storeAccess, features, cashLedger, audit, Clock.systemUTC());
    }

    LotteryPosActivityService(LotteryPosActivityRepository repository, RegisterSessionRepository sessions,
                              StoreAccessService storeAccess, FeatureService features,
                              CashLedgerService cashLedger, AuditService audit, Clock clock) {
        this.repository = repository;
        this.sessions = sessions;
        this.storeAccess = storeAccess;
        this.features = features;
        this.cashLedger = cashLedger;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public LotteryPosActivityResponse record(LotteryPosActivityRequest request, Authentication authentication) {
        LotteryPosActivity existing = repository.findByOperationId(request.operationId()).orElse(null);
        if (existing != null) return LotteryPosActivityResponse.from(existing);

        User actor = storeAccess.currentTenantUser(authentication);
        RegisterSession session = sessions.findByIdForUpdate(request.registerSessionId())
                .orElseThrow(() -> new NotFoundException("Register session not found"));
        if (!session.getStore().getTenantId().equals(actor.getTenantId()) || !storeAccess.canAccessStore(actor.getId(), session.getStore().getId())) {
            throw new ForbiddenOperationException("Register session is outside the current merchant");
        }
        if (session.getRegister().getType() != RegisterType.RETAIL) throw new ConflictException("Lottery actions require a Retail register session");
        if (session.getStatus() != RegisterSessionStatus.OPEN) throw new ConflictException("Register session is not open");
        if (!session.isBusinessDayOperational()) throw new ConflictException("BUSINESS_DAY_NOT_OPEN");
        if (!session.getStore().isActive() || !session.getRegister().isActive()) throw new ConflictException("Store and register must be active");
        boolean elevated = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_OWNER") || a.getAuthority().equals("ROLE_MANAGER"));
        if (!elevated && !session.getAssignedCashier().getId().equals(actor.getId())) {
            throw new ForbiddenOperationException("Lottery action user must be assigned to this register session");
        }
        features.requireEnabled(FeatureCode.LOTTERY_SALES, session.getStore().getId(), session.getRegister().getId());

        Instant occurredAt = Instant.now(clock);
        var activity = repository.saveAndFlush(new LotteryPosActivity(session, actor, request.type(),
                request.amount().setScale(2, RoundingMode.UNNECESSARY),
                session.getStore().getCurrencyCode().trim().toUpperCase(Locale.ROOT),
                session.getBusinessDay().getBusinessDate(), occurredAt, request.operationId()));
        cashLedger.append(new CashLedgerEntryCommand(session.getStore(), session.getRegister(), session,
                request.type() == LotteryPosActivityType.SOLD ? CashLedgerSourceType.LOTTERY_SALE_CASH : CashLedgerSourceType.LOTTERY_PAYOUT_CASH,
                activity.getId(), request.type() == LotteryPosActivityType.SOLD ? CashLedgerDirection.IN : CashLedgerDirection.OUT,
                activity.getAmount(), activity.getCurrencyCode(), activity.getBusinessDate(), occurredAt, actor,
                request.operationId(), request.type() == LotteryPosActivityType.SOLD ? "Manual Lottery Sold" : "Lottery Win payout"));
        LotteryPosActivityResponse response = LotteryPosActivityResponse.from(activity);
        audit.record(new CreateAuditRecordCommand(actor.getId(),
                request.type() == LotteryPosActivityType.SOLD ? AuditAction.LOTTERY_SALE_RECORDED : AuditAction.LOTTERY_PAYOUT_PAID,
                "LOTTERY_POS_ACTIVITY", activity.getId(), activity.getStore().getId(), activity.getRegister().getId(),
                null, response, "MANUAL_POS"));
        return response;
    }
}
