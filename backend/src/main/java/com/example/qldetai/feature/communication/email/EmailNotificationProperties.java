package com.example.qldetai.feature.communication.email;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.email")
public class EmailNotificationProperties {

    private boolean enabled;
    private String defaultFrom = "thongbao@hackerlo.online";
    private String allowedFromAddresses = "thongbao@hackerlo.online";
    private String baseUrl = "http://localhost:5173";
    private String displayTimeZone = "Asia/Ho_Chi_Minh";
    private long workerDelayMs = 5000;
    private int maxPerRun = 10;
    private int maxAttempts = 4;
    private long retryBaseDelaySeconds = 60;
    private int claimTimeoutMinutes = 15;
    private long deadlineScanDelayMs = 900000;
    private int deadlineScanWindowMinutes = 20;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDefaultFrom() {
        return defaultFrom;
    }

    public void setDefaultFrom(String defaultFrom) {
        this.defaultFrom = defaultFrom;
    }

    public String getAllowedFromAddresses() {
        return allowedFromAddresses;
    }

    public void setAllowedFromAddresses(String allowedFromAddresses) {
        this.allowedFromAddresses = allowedFromAddresses;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getDisplayTimeZone() {
        return displayTimeZone;
    }

    public void setDisplayTimeZone(String displayTimeZone) {
        this.displayTimeZone = displayTimeZone;
    }

    public long getWorkerDelayMs() {
        return workerDelayMs;
    }

    public void setWorkerDelayMs(long workerDelayMs) {
        this.workerDelayMs = workerDelayMs;
    }

    public int getMaxPerRun() {
        return maxPerRun;
    }

    public void setMaxPerRun(int maxPerRun) {
        this.maxPerRun = maxPerRun;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public long getRetryBaseDelaySeconds() {
        return retryBaseDelaySeconds;
    }

    public void setRetryBaseDelaySeconds(long retryBaseDelaySeconds) {
        this.retryBaseDelaySeconds = retryBaseDelaySeconds;
    }

    public int getClaimTimeoutMinutes() {
        return claimTimeoutMinutes;
    }

    public void setClaimTimeoutMinutes(int claimTimeoutMinutes) {
        this.claimTimeoutMinutes = claimTimeoutMinutes;
    }

    public long getDeadlineScanDelayMs() {
        return deadlineScanDelayMs;
    }

    public void setDeadlineScanDelayMs(long deadlineScanDelayMs) {
        this.deadlineScanDelayMs = deadlineScanDelayMs;
    }

    public int getDeadlineScanWindowMinutes() {
        return deadlineScanWindowMinutes;
    }

    public void setDeadlineScanWindowMinutes(int deadlineScanWindowMinutes) {
        this.deadlineScanWindowMinutes = deadlineScanWindowMinutes;
    }

    public ZoneId displayZoneId() {
        return ZoneId.of(displayTimeZone);
    }
}
