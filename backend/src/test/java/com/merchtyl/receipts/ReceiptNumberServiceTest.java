package com.merchtyl.receipts;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReceiptNumberServiceTest {
    @Test
    void allocatesShortHumanReadableNumberFromDatabaseSequence() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(contains("nextval"), eq(Long.class))).thenReturn(1024L);

        assertThat(new ReceiptNumberService(jdbc).nextNumber()).isEqualTo("1024");
    }

    @Test
    void databaseSequenceAllocationsRemainDistinct() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(contains("nextval"), eq(Long.class))).thenReturn(1024L, 1025L);
        ReceiptNumberService service = new ReceiptNumberService(jdbc);

        assertThat(service.nextNumber()).isEqualTo("1024");
        assertThat(service.nextNumber()).isEqualTo("1025");
    }

    @Test
    void concurrentAllocationsCannotReturnDuplicateNumbers() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AtomicLong databaseSequence = new AtomicLong(999);
        when(jdbc.queryForObject(contains("nextval"), eq(Long.class)))
                .thenAnswer(ignored -> databaseSequence.incrementAndGet());
        ReceiptNumberService service = new ReceiptNumberService(jdbc);
        var allocated = ConcurrentHashMap.<String>newKeySet();

        IntStream.range(0, 100).parallel().forEach(ignored -> allocated.add(service.nextNumber()));

        assertThat(allocated).hasSize(100);
        assertThat(allocated).contains("1000", "1099");
    }
}
