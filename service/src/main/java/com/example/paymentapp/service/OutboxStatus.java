package com.example.paymentapp.service;

public final class OutboxStatus {
  public static final String PENDING = "pending";
  public static final String PROCESSING = "processing";
  public static final String DONE = "done";
  public static final String FAILED = "failed";

  private OutboxStatus() {}
}
