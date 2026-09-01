package com.merchtyl.platform.billing;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class BillingQuantityService {
    private final JdbcTemplate jdbc;

    public BillingQuantityService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int quantity(UUID tenantId, CommercialCapability capability, BillingUnit unit) {
        return switch (unit) {
            case PER_MERCHANT -> 1;
            case PER_STORE -> storeCount(tenantId, capability);
            case PER_USER -> billableUserCount(tenantId);
            case PER_REGISTER -> registerCount(tenantId, capability);
        };
    }

    int billableUserCount(UUID tenantId) {
        return count("select count(*) from security_users where tenant_id=? and enabled=true and locked=false", tenantId);
    }

    int storeCount(UUID tenantId, CommercialCapability capability) {
        return switch (capability) {
            case FOOD_SERVICE -> count("select count(distinct s.id) from stores s join store_capabilities c on c.store_id=s.id where s.tenant_id=? and s.active=true and c.capability='FOOD_SERVICE'", tenantId);
            case LOTTERY -> count("select count(distinct s.id) from stores s join store_capabilities c on c.store_id=s.id where s.tenant_id=? and s.active=true and c.capability='LOTTERY'", tenantId);
            default -> count("select count(*) from stores where tenant_id=? and active=true", tenantId);
        };
    }

    int registerCount(UUID tenantId, CommercialCapability capability) {
        return switch (capability) {
            case FOOD_SERVICE -> count("select count(distinct r.id) from registers r join stores s on s.id=r.store_id join store_capabilities c on c.store_id=s.id where s.tenant_id=? and s.active=true and r.active=true and c.capability='FOOD_SERVICE'", tenantId);
            case LOTTERY -> count("select count(distinct r.id) from registers r join stores s on s.id=r.store_id join store_capabilities c on c.store_id=s.id where s.tenant_id=? and s.active=true and r.active=true and c.capability='LOTTERY'", tenantId);
            default -> count("select count(*) from registers r join stores s on s.id=r.store_id where s.tenant_id=? and s.active=true and r.active=true", tenantId);
        };
    }

    private int count(String sql, UUID tenantId) {
        Integer value = jdbc.queryForObject(sql, Integer.class, tenantId);
        return value == null ? 0 : value;
    }
}
