package com.example.paymentapp.service;

import com.example.paymentapp.config.PaymentRetryProperties;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

@Component
public class BackoffCalculator {
  private final PaymentRetryProperties retryProperties;

  public BackoffCalculator(PaymentRetryProperties retryProperties) {
    this.retryProperties = retryProperties;
  }

  public Duration nextDelay(int attemptNumber) {
    int safeAttempt = Math.max(1, attemptNumber);
    double baseDelay = retryProperties.getBaseDelayMs();
    double multiplier = Math.pow(retryProperties.getMultiplier(), safeAttempt - 1);
    double jitter =
        ThreadLocalRandom.current().nextDouble(
            retryProperties.getJitterMin(), retryProperties.getJitterMax());
    long delayMs = (long) Math.max(0, baseDelay * multiplier * jitter);
    return Duration.ofMillis(delayMs);
  }
}
