package com.example.paymentapp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "outbox")
public class Outbox {
  @Id
  @UuidGenerator
  @Column(nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "payment_id", nullable = false)
  private Payment payment;

  @Column(columnDefinition = "jsonb", nullable = false)
  private String payload;

  @Column(nullable = false, length = 16)
  private String status;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "next_attempt_at", nullable = false)
  private OffsetDateTime nextAttemptAt;

  @Column(name = "claimed_by")
  private String claimedBy;

  @Column(name = "claimed_at")
  private OffsetDateTime claimedAt;

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

  public Payment getPayment() {
    return payment;
  }

  public void setPayment(Payment payment) {
    this.payment = payment;
  }

  public String getPayload() {
    return payload;
  }

  public void setPayload(String payload) {
    this.payload = payload;
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

  public OffsetDateTime getNextAttemptAt() {
    return nextAttemptAt;
  }

  public void setNextAttemptAt(OffsetDateTime nextAttemptAt) {
    this.nextAttemptAt = nextAttemptAt;
  }

  public String getClaimedBy() {
    return claimedBy;
  }

  public void setClaimedBy(String claimedBy) {
    this.claimedBy = claimedBy;
  }

  public OffsetDateTime getClaimedAt() {
    return claimedAt;
  }

  public void setClaimedAt(OffsetDateTime claimedAt) {
    this.claimedAt = claimedAt;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }
}
