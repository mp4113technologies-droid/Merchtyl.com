package com.merchtyl.platform.web;

import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;

class RequestSqlStatementCounterTest {
    @Test
    void countsOnlyStatementsInsideCurrentRequestAndClearsThreadState() {
        RequestSqlStatementCounter counter = new RequestSqlStatementCounter();
        counter.inspect("select outside request");

        counter.begin();
        assertThat(counter.inspect("select one")).isEqualTo("select one");
        counter.inspect("select two");

        assertThat(counter.finish()).isEqualTo(2);
        assertThat(counter.finish()).isZero();
    }

    @Test
    void registersAsHibernateStatementInspector() {
        RequestSqlStatementCounter counter = new RequestSqlStatementCounter();
        var properties = new HashMap<String, Object>();

        counter.customize(properties);

        assertThat(properties).containsValue(counter);
    }
}
