package com.merchtyl.platform.web;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Counts SQL statements on the request thread without logging SQL or bind values. */
@Component
public class RequestSqlStatementCounter implements StatementInspector, HibernatePropertiesCustomizer {
    private final ThreadLocal<Integer> count = new ThreadLocal<>();

    public void begin() {
        count.set(0);
    }

    public int finish() {
        Integer result = count.get();
        count.remove();
        return result == null ? 0 : result;
    }

    @Override
    public String inspect(String sql) {
        Integer current = count.get();
        if (current != null) count.set(current + 1);
        return sql;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(AvailableSettings.STATEMENT_INSPECTOR, this);
    }
}
