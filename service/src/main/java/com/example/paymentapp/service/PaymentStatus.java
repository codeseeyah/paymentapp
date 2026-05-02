package com.example.paymentapp.service;

public final class PaymentStatus {
  public static final String RECEIVED = "received";
  public static final String PROCESSING = "processing";
  public static final String COMPLETED = "completed";
  public static final String FAILED = "failed";

  private PaymentStatus() {}
}
