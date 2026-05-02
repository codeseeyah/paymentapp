package com.example.paymentapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment.simulator")
public class PaymentSimulatorProperties {
  private long minDelayMs = 10;
  private long maxDelayMs = 2000;
  private double failureRate = 0.1;

  public long getMinDelayMs() {
    return minDelayMs;
  }

  public void setMinDelayMs(long minDelayMs) {
    this.minDelayMs = minDelayMs;
  }

  public long getMaxDelayMs() {
    return maxDelayMs;
  }

  public void setMaxDelayMs(long maxDelayMs) {
    this.maxDelayMs = maxDelayMs;
  }

  public double getFailureRate() {
    return failureRate;
  }

  public void setFailureRate(double failureRate) {
    this.failureRate = failureRate;
  }
}
