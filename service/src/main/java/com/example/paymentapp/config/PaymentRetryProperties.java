package com.example.paymentapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment.retry")
public class PaymentRetryProperties {
  private long baseDelayMs = 200;
  private double multiplier = 2.0;
  private double jitterMin = 0.5;
  private double jitterMax = 1.0;
  private int maxAttempts = 5;

  public long getBaseDelayMs() {
    return baseDelayMs;
  }

  public void setBaseDelayMs(long baseDelayMs) {
    this.baseDelayMs = baseDelayMs;
  }

  public double getMultiplier() {
    return multiplier;
  }

  public void setMultiplier(double multiplier) {
    this.multiplier = multiplier;
  }

  public double getJitterMin() {
    return jitterMin;
  }

  public void setJitterMin(double jitterMin) {
    this.jitterMin = jitterMin;
  }

  public double getJitterMax() {
    return jitterMax;
  }

  public void setJitterMax(double jitterMax) {
    this.jitterMax = jitterMax;
  }

  public int getMaxAttempts() {
    return maxAttempts;
  }

  public void setMaxAttempts(int maxAttempts) {
    this.maxAttempts = maxAttempts;
  }
}
