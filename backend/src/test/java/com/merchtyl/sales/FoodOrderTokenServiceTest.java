package com.merchtyl.sales;

import com.merchtyl.eod.BusinessDay;
import com.merchtyl.registersession.RegisterSession;
import com.merchtyl.store.Store;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FoodOrderTokenServiceTest {
    @Test
    void allocatesFormattedTokenUsingAtomicPostgresUpsert() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RegisterSession session = mock(RegisterSession.class);
        Store store = mock(Store.class);
        BusinessDay businessDay = mock(BusinessDay.class);
        UUID storeId = UUID.randomUUID();
        UUID businessDayId = UUID.randomUUID();
        when(session.getStore()).thenReturn(store);
        when(session.getBusinessDay()).thenReturn(businessDay);
        when(store.getId()).thenReturn(storeId);
        when(businessDay.getId()).thenReturn(businessDayId);
        when(jdbcTemplate.queryForObject(contains("ON CONFLICT (store_id, business_day_id)"), eq(Long.class),
                eq(storeId), eq(businessDayId))).thenReturn(104L);

        String token = new FoodOrderTokenService(jdbcTemplate).nextToken(session);

        assertThat(token).isEqualTo("A104");
    }

    @Test
    void sequenceResultProducesDistinctTokens() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RegisterSession session = mock(RegisterSession.class);
        Store store = mock(Store.class);
        BusinessDay businessDay = mock(BusinessDay.class);
        when(session.getStore()).thenReturn(store);
        when(session.getBusinessDay()).thenReturn(businessDay);
        when(store.getId()).thenReturn(UUID.randomUUID());
        when(businessDay.getId()).thenReturn(UUID.randomUUID());
        when(jdbcTemplate.queryForObject(any(String.class), eq(Long.class), any(), any()))
                .thenReturn(1L, 2L);
        FoodOrderTokenService service = new FoodOrderTokenService(jdbcTemplate);

        assertThat(service.nextToken(session)).isEqualTo("A001");
        assertThat(service.nextToken(session)).isEqualTo("A002");
    }
}
