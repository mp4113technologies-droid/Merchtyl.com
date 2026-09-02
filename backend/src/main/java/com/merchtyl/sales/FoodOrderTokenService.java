package com.merchtyl.sales;

import com.merchtyl.common.ConflictException;
import com.merchtyl.registersession.RegisterSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class FoodOrderTokenService {
    private final JdbcTemplate jdbcTemplate;

    public FoodOrderTokenService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String nextToken(RegisterSession session) {
        if (session.getBusinessDay() == null) {
            throw new ConflictException("OPEN_BUSINESS_DAY_REQUIRED_FOR_FOOD_ORDER_TOKEN");
        }
        Long value = jdbcTemplate.queryForObject("""
                INSERT INTO food_order_token_sequences (store_id, business_day_id, last_value)
                VALUES (?, ?, 1)
                ON CONFLICT (store_id, business_day_id)
                DO UPDATE SET last_value = food_order_token_sequences.last_value + 1
                RETURNING last_value
                """, Long.class, session.getStore().getId(), session.getBusinessDay().getId());
        if (value == null) {
            throw new IllegalStateException("Food order token allocation returned no value");
        }
        return "A%03d".formatted(value);
    }
}
