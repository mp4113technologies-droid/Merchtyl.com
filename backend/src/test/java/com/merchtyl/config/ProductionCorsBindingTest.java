package com.merchtyl.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionCorsBindingTest {
    @Test
    void commaSeparatedRailwayVariablesBindAsSeparateLists() {
        var source = new MapConfigurationPropertySource(Map.of(
                "merchtyl.security.cors.allowed-origins", "https://merchtyl.com,https://www.merchtyl.com,https://platform.merchtyl.com",
                "merchtyl.security.cors.allowed-origin-patterns", "https://*.merchtyl.com"));
        Binder binder = new Binder(source);
        var origins = binder.bind("merchtyl.security.cors.allowed-origins", Bindable.listOf(String.class)).get();
        var patterns = binder.bind("merchtyl.security.cors.allowed-origin-patterns", Bindable.listOf(String.class)).get();
        assertThat(origins).containsExactly(
                "https://merchtyl.com", "https://www.merchtyl.com", "https://platform.merchtyl.com");
        assertThat(patterns).containsExactly("https://*.merchtyl.com");
    }

    @Test
    void defensiveNormalizationSplitsACommaSeparatedSingleElement() {
        SecurityProperties.Cors cors = new SecurityProperties.Cors(
                java.util.List.of(" https://merchtyl.com, https://www.merchtyl.com,https://platform.merchtyl.com "),
                java.util.List.of(" https://*.merchtyl.com "));
        assertThat(cors.allowedOrigins()).containsExactly(
                "https://merchtyl.com", "https://www.merchtyl.com", "https://platform.merchtyl.com");
        assertThat(cors.allowedOriginPatterns()).containsExactly("https://*.merchtyl.com");
    }

    @Test
    void dedicatedCorsPropertiesParseTheExactRailwayFormat() {
        CorsProperties cors = new CorsProperties(
                "https://merchtyl.com,https://www.merchtyl.com,https://platform.merchtyl.com",
                "https://*.merchtyl.com");
        assertThat(cors.exactOrigins()).containsExactly(
                "https://merchtyl.com", "https://www.merchtyl.com", "https://platform.merchtyl.com");
        assertThat(cors.originPatterns()).containsExactly("https://*.merchtyl.com");
    }
}
