package com.merchtyl.platform.billing;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class RegisterOverageService {
    private final JdbcTemplate jdbc;

    public RegisterOverageService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public RegisterOverage calculate(UUID tenantId, Integer includedRegistersPerStore) {
        List<StoreRegisterUsage> stores=jdbc.query("""
                select s.id,s.name,count(r.id) active_registers
                from stores s
                left join registers r on r.store_id=s.id and r.active=true
                where s.tenant_id=? and s.active=true
                group by s.id,s.name
                order by s.name,s.id
                """,(rs,row)->new StoreRegisterUsage(rs.getObject(1,UUID.class),rs.getString(2),rs.getInt(3),0),tenantId);
        return calculate(stores,includedRegistersPerStore);
    }

    static RegisterOverage calculate(List<StoreRegisterUsage> groupedCounts,Integer includedRegistersPerStore){
        int allowance=includedRegistersPerStore==null?Integer.MAX_VALUE:includedRegistersPerStore;
        List<StoreRegisterUsage> stores=groupedCounts.stream().map(store->new StoreRegisterUsage(store.storeId(),store.storeName(),store.activeRegisters(),Math.max(0,store.activeRegisters()-allowance))).toList();
        return new RegisterOverage(stores,stores.stream().mapToInt(StoreRegisterUsage::activeRegisters).sum(),stores.stream().mapToInt(StoreRegisterUsage::additionalRegisters).sum());
    }

    public record StoreRegisterUsage(UUID storeId,String storeName,int activeRegisters,int additionalRegisters) {}
    public record RegisterOverage(List<StoreRegisterUsage> stores,int activeRegisters,int additionalRegisters) {}
}
