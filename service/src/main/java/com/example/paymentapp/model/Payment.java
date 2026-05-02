package com.example.paymentapp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "payments")
public class Payment {
  @Id
  @UuidGenerator
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(name = "idempotency_key")
  private String idempotencyKey;

  @Column(nullable = false, precision = 18, scale = 4)
  private BigDecimal amount;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "payer_id")
  private String payerId;

  @Column(name = "payee_id")
  private String payeeId;

  @Column(nullable = false, length = 16)
  private String status;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "last_attempted_at")
  private OffsetDateTime lastAttemptedAt;

  @Column(name = "external_response", columnDefinition = "jsonb")
  private String externalResponse;

  @Column(name = "failure_reason")
  private String failureReason;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @PrePersist
  void onCreate() {
    OffsetDateTime now = OffsetDateTime.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = OffsetDateTime.now();
  }

  public UUID getId() {
    return id;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public void setIdempotencyKey(String idempotencyKey) {
    this.idempotencyKey = idempotencyKey;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal amount) {
    this.amount = amount;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public String getPayerId() {
    return payerId;
  }

  public void setPayerId(String payerId) {
    this.payerId = payerId;
  }

  public String getPayeeId() {
    return payeeId;
  }

  public void setPayeeId(String payeeId) {
    this.payeeId = payeeId;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public int getAttemptCount() {
    return attemptCount;
  }

  public void setAttemptCount(int attemptCount) {
    this.attemptCount = attemptCount;
  }

  public OffsetDateTime getLastAttemptedAt() {
    return lastAttemptedAt;
  }

  public void setLastAttemptedAt(OffsetDateTime lastAttemptedAt) {
    this.lastAttemptedAt = lastAttemptedAt;
  }

  public String getExternalResponse() {
    return externalResponse;
  }

  public void setExternalResponse(String externalResponse) {
    this.externalResponse = externalResponse;
  }

  public String getFailureReason() {
    return failureReason;
  }

  public void setFailureReason(String failureReason) {
    this.failureReason = failureReason;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }
}
