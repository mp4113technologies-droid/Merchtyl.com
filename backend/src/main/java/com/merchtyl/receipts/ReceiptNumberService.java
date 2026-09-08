package com.merchtyl.receipts;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ReceiptNumberService {
    private final JdbcTemplate jdbcTemplate;

    public ReceiptNumberService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String nextNumber() {
        Long value = jdbcTemplate.queryForObject("SELECT nextval('receipt_number_sequence')", Long.class);
        if (value == null) {
            throw new IllegalStateException("Receipt number allocation returned no value");
        }
        return value.toString();
    }
}
