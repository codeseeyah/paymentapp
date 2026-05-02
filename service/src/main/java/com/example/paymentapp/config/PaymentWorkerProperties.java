package com.example.paymentapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment.worker")
public class PaymentWorkerProperties {
  private int batchSize = 10;
  private long pollIntervalMs = 500;
  private long leaseTimeoutMs = 30000;
  private boolean scheduled = true;

  public int getBatchSize() {
    return batchSize;
  }

  public void setBatchSize(int batchSize) {
    this.batchSize = batchSize;
  }

  public long getPollIntervalMs() {
    return pollIntervalMs;
  }

  public void setPollIntervalMs(long pollIntervalMs) {
    this.pollIntervalMs = pollIntervalMs;
  }

  public long getLeaseTimeoutMs() {
    return leaseTimeoutMs;
  }

  public void setLeaseTimeoutMs(long leaseTimeoutMs) {
    this.leaseTimeoutMs = leaseTimeoutMs;
  }

  public boolean isScheduled() {
    return scheduled;
  }

  public void setScheduled(boolean scheduled) {
    this.scheduled = scheduled;
  }
}
