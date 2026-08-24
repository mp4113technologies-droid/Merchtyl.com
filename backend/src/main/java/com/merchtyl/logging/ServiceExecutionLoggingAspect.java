package com.merchtyl.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Aspect
@Component
public class ServiceExecutionLoggingAspect {
    private static final Logger log = LoggerFactory.getLogger(ServiceExecutionLoggingAspect.class);

    private static final Map<String, String> IMPORTANT_OPERATIONS = Map.ofEntries(
            Map.entry("PlatformAdministrationService.createMerchant", "Create Merchant"),
            Map.entry("StoreService.create", "Create Store"),
            Map.entry("StoreService.update", "Update Store"),
            Map.entry("UserAdministrationService.create", "Create User"),
            Map.entry("UserAdministrationService.update", "Update User"),
            Map.entry("UserAdministrationService.updateStatus", "User Status Change"),
            Map.entry("UserAdministrationService.resetPassword", "Password Reset"),
            Map.entry("UserAdministrationService.replaceRolesAndAssignments", "Role or Store Assignment Changed"),
            Map.entry("ProductService.create", "Create Product"),
            Map.entry("InventoryService.recordStockChange", "Inventory Adjustment"),
            Map.entry("StockAdjustmentService.create", "Inventory Adjustment"),
            Map.entry("StockCountService.create", "Inventory Count"),
            Map.entry("StockCountService.post", "Inventory Count"),
            Map.entry("SaleService.createDraft", "Create Sale"),
            Map.entry("SaleService.completeIdempotently", "Complete Sale"),
            Map.entry("RegisterSessionService.open", "Open Register"),
            Map.entry("RegisterSessionService.close", "Close Register"),
            Map.entry("CashMovementService.create", "Cash Register Event"),
            Map.entry("BusinessDayService.startClosing", "Generate EOD"),
            Map.entry("BusinessDayService.close", "Generate EOD"),
            Map.entry("BusinessDayService.forceClose", "Generate EOD"),
            Map.entry("BusinessDayService.exportCsv", "Export Report"),
            Map.entry("BusinessDayService.exportPdf", "Export Report"),
            Map.entry("LotterySaleService.recordIdempotently", "Lottery Sale"),
            Map.entry("LotterySaleService.cancelIdempotently", "Lottery Cancelled"),
            Map.entry("LotteryPayoutService.create", "Lottery Payout"),
            Map.entry("LotteryPayoutService.completeCashIdempotently", "Lottery Payout"),
            Map.entry("EmailDeliveryService.sendTestEmail", "Email Sending"));

    @Around("execution(public * com.merchtyl..*Service.*(..))"
            + " && !within(com.merchtyl.logging..*)"
            + " && !within(com.merchtyl.audit.AuditService)")
    public Object logServiceExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        String key = joinPoint.getSignature().getDeclaringType().getSimpleName() + "." + joinPoint.getSignature().getName();
        String operation = IMPORTANT_OPERATIONS.get(key);
        long started = System.nanoTime();
        if (operation == null) {
            log.debug("service_execution START service={} method={}",
                    joinPoint.getSignature().getDeclaringType().getSimpleName(),
                    joinPoint.getSignature().getName());
        } else {
            log.info("business_operation START operation={} service_method={}", operation, key);
        }
        try {
            Object result = joinPoint.proceed();
            long durationMs = (System.nanoTime() - started) / 1_000_000;
            if (operation == null) {
                log.debug("service_execution END service={} method={} duration_ms={}",
                        joinPoint.getSignature().getDeclaringType().getSimpleName(),
                        joinPoint.getSignature().getName(),
                        durationMs);
            } else {
                log.info("business_operation END operation={} service_method={} duration_ms={}",
                        operation,
                        key,
                        durationMs);
            }
            return result;
        } catch (Throwable exception) {
            long durationMs = (System.nanoTime() - started) / 1_000_000;
            log.warn("service_execution FAILED service_method={} operation={} duration_ms={} exception_type={}",
                    key,
                    operation == null ? "" : operation,
                    durationMs,
                    exception.getClass().getName());
            throw exception;
        }
    }
}
