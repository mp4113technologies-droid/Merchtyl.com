package com.merchtyl.logging;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "merchtyl.logging")
public class MerchtylLoggingProperties {
    private Toggle json = new Toggle(false);
    private Toggle request = new Toggle(true);
    private Toggle response = new Toggle(true);
    private MaskSensitive maskSensitive = new MaskSensitive(true);
    private Performance performance = new Performance(true, 1000);

    public Toggle getJson() {
        return json;
    }

    public void setJson(Toggle json) {
        this.json = json == null ? new Toggle(false) : json;
    }

    public Toggle getRequest() {
        return request;
    }

    public void setRequest(Toggle request) {
        this.request = request == null ? new Toggle(true) : request;
    }

    public Toggle getResponse() {
        return response;
    }

    public void setResponse(Toggle response) {
        this.response = response == null ? new Toggle(true) : response;
    }

    public MaskSensitive getMaskSensitive() {
        return maskSensitive;
    }

    public void setMaskSensitive(MaskSensitive maskSensitive) {
        this.maskSensitive = maskSensitive == null ? new MaskSensitive(true) : maskSensitive;
    }

    public Performance getPerformance() {
        return performance;
    }

    public void setPerformance(Performance performance) {
        this.performance = performance == null ? new Performance(true, 1000) : performance;
    }

    public static class Toggle {
        private boolean enabled;

        public Toggle() {
        }

        public Toggle(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class MaskSensitive extends Toggle {
        public MaskSensitive() {
        }

        public MaskSensitive(boolean enabled) {
            super(enabled);
        }
    }

    public static class Performance extends Toggle {
        private long slowRequestThresholdMs;

        public Performance() {
        }

        public Performance(boolean enabled, long slowRequestThresholdMs) {
            super(enabled);
            this.slowRequestThresholdMs = slowRequestThresholdMs;
        }

        public long getSlowRequestThresholdMs() {
            return slowRequestThresholdMs;
        }

        public void setSlowRequestThresholdMs(long slowRequestThresholdMs) {
            this.slowRequestThresholdMs = slowRequestThresholdMs;
        }
    }
}
