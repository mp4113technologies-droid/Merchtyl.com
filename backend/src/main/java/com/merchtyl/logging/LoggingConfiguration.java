package com.merchtyl.logging;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MerchtylLoggingProperties.class)
public class LoggingConfiguration {
}
