package com.merchtyl.sales;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class SalesClassificationServiceTest {
    @Test
    void aggregatesExactMixedTransactionAndRefundFromDatabaseProjections() throws Exception {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        UUID sessionId = UUID.randomUUID();
        int[] call = {0};
        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(2);
            ResultSet row = mock(ResultSet.class);
            org.mockito.Mockito.when(row.getObject("session_id", UUID.class)).thenReturn(sessionId);
            if (call[0]++ == 0) {
                org.mockito.Mockito.when(row.getBigDecimal("taxable_sales")).thenReturn(new BigDecimal("21.00"));
                org.mockito.Mockito.when(row.getBigDecimal("non_taxable_sales")).thenReturn(new BigDecimal("12.00"));
                org.mockito.Mockito.when(row.getBigDecimal("tax_collected")).thenReturn(new BigDecimal("3.15"));
            } else {
                org.mockito.Mockito.when(row.getBigDecimal("taxable_sales")).thenReturn(new BigDecimal("0.00"));
                org.mockito.Mockito.when(row.getBigDecimal("non_taxable_sales")).thenReturn(new BigDecimal("0.00"));
                org.mockito.Mockito.when(row.getBigDecimal("tax_collected")).thenReturn(new BigDecimal("0.00"));
            }
            handler.processRow(row);
            return null;
        }).when(jdbc).query(anyString(), any(SqlParameterSource.class), any(RowCallbackHandler.class));

        Map<UUID, SalesClassification> result = new SalesClassificationService(jdbc)
                .byRegisterSessions(UUID.randomUUID(), List.of(sessionId));

        assertThat(result.get(sessionId).taxableSales()).isEqualByComparingTo("21.00");
        assertThat(result.get(sessionId).nonTaxableSales()).isEqualByComparingTo("12.00");
        assertThat(result.get(sessionId).merchandiseNetSales()).isEqualByComparingTo("33.00");
        assertThat(result.get(sessionId).taxCollected()).isEqualByComparingTo("3.15");

        org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(2)).query(sql.capture(), any(SqlParameterSource.class), any(RowCallbackHandler.class));
        assertThat(sql.getAllValues()).allSatisfy(value -> {
            assertThat(value).doesNotContain("%s", "ANDsi");
            assertThat(value).contains(" AND si.line_type");
        });
    }
}
