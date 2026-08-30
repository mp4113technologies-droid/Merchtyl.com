package com.merchtyl.platform.billing;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

@Component
public class PlatformBillingScheduler {
    private final JdbcTemplate jdbc;
    private final PlatformBillingService billing;
    private final PlatformInvoiceEmailService email;

    public PlatformBillingScheduler(JdbcTemplate jdbc, PlatformBillingService billing, PlatformInvoiceEmailService email) {
        this.jdbc = jdbc;
        this.billing = billing;
        this.email = email;
    }

    @Scheduled(cron = "${merchtyl.billing.invoice-cron:0 15 2 * * *}", zone = "UTC")
    public void generateDueInvoices() {
        billing.activateDuePricingVersions();
        jdbc.update("""
                update tenant_subscriptions set status='ACTIVE',updated_at=now(),version=version+1
                where status='TRIAL' and trial_ends_at is not null and trial_ends_at<=now()
                """);
        var tenantIds = jdbc.query("""
                select tenant_id from tenant_subscriptions
                where status in ('ACTIVE','PAST_DUE') and next_billing_date <= current_date
                order by next_billing_date for update skip locked
                """, (rs, row) -> rs.getObject(1, UUID.class));
        for (UUID tenantId : tenantIds) {
            billing.adoptDuePricingVersion(tenantId);
            BillingDtos.InvoiceResponse invoice = billing.generateInvoice(
                    tenantId, new BillingDtos.InvoiceGenerateRequest(null, null, null), (UUID) null);
            email.sendSystem(invoice.id());
        }
    }

    @Scheduled(cron = "${merchtyl.billing.maintenance-cron:0 45 2 * * *}", zone = "UTC")
    public void maintainBilling() {
        billing.markPastDue();
        jdbc.update("""
                update tenant_subscriptions set status='CANCELLED',cancelled_at=now(),updated_at=now(),version=version+1
                where cancel_at_period_end=true and current_period_end < ? and status <> 'CANCELLED'
                """, java.sql.Date.valueOf(LocalDate.now()));
    }
}
