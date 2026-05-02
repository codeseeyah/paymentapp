package com.example.paymentapp.client;

public class ExternalPaymentException extends RuntimeException {
  private final boolean retryable;

  public ExternalPaymentException(String message, boolean retryable) {
    super(message);
    this.retryable = retryable;
  }

  public ExternalPaymentException(String message, boolean retryable, Throwable cause) {
    super(message, cause);
    this.retryable = retryable;
  }

  public boolean isRetryable() {
    return retryable;
  }
}
