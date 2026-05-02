package com.example.paymentapp.dto;

import com.example.paymentapp.model.Payment;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public class PaymentResource {
  private UUID id;
  private String idempotencyKey;
  private BigDecimal amount;
  private String currency;
  private String status;
  private int attemptCount;
  private String externalResponse;
  private String failureReason;
  private OffsetDateTime createdAt;
  private OffsetDateTime updatedAt;

  public static PaymentResource fromPayment(Payment payment) {
    PaymentResource resource = new PaymentResource();
    resource.id = payment.getId();
    resource.idempotencyKey = payment.getIdempotencyKey();
    resource.amount = payment.getAmount();
    resource.currency = payment.getCurrency();
    resource.status = payment.getStatus();
    resource.attemptCount = payment.getAttemptCount();
    resource.externalResponse = payment.getExternalResponse();
    resource.failureReason = payment.getFailureReason();
    resource.createdAt = payment.getCreatedAt();
    resource.updatedAt = payment.getUpdatedAt();
    return resource;
  }

  public UUID getId() {
    return id;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getCurrency() {
    return currency;
  }

  public String getStatus() {
    return status;
  }

  public int getAttemptCount() {
    return attemptCount;
  }

  public String getExternalResponse() {
    return externalResponse;
  }

  public String getFailureReason() {
    return failureReason;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }
}
