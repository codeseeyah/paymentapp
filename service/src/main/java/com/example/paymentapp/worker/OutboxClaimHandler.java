package com.example.paymentapp.worker;

import com.example.paymentapp.config.PaymentRetryProperties;
import com.example.paymentapp.config.PaymentWorkerProperties;
import com.example.paymentapp.dto.PaymentRequest;
import com.example.paymentapp.metrics.OutboxMetrics;
import com.example.paymentapp.model.Outbox;
import com.example.paymentapp.model.Payment;
import com.example.paymentapp.repository.OutboxRepository;
import com.example.paymentapp.repository.PaymentRepository;
import com.example.paymentapp.service.BackoffCalculator;
import com.example.paymentapp.service.OutboxStatus;
import com.example.paymentapp.service.PaymentStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxClaimHandler {
  private final OutboxRepository outboxRepository;
  private final PaymentRepository paymentRepository;
  private final BackoffCalculator backoffCalculator;
  private final PaymentRetryProperties retryProperties;
  private final PaymentWorkerProperties workerProperties;
  private final OutboxMetrics outboxMetrics;
  private final ObjectMapper objectMapper;
  private final String workerId = UUID.randomUUID().toString();

  public OutboxClaimHandler(
      OutboxRepository outboxRepository,
      PaymentRepository paymentRepository,
      BackoffCalculator backoffCalculator,
      PaymentRetryProperties retryProperties,
      PaymentWorkerProperties workerProperties,
      OutboxMetrics outboxMetrics,
      ObjectMapper objectMapper) {
    this.outboxRepository = outboxRepository;
    this.paymentRepository = paymentRepository;
    this.backoffCalculator = backoffCalculator;
    this.retryProperties = retryProperties;
    this.workerProperties = workerProperties;
    this.outboxMetrics = outboxMetrics;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public List<OutboxWorker.OutboxClaim> claimBatch(int limit) {
    OffsetDateTime cutoff =
        OffsetDateTime.now().minusNanos(workerProperties.getLeaseTimeoutMs() * 1_000_000L);
    List<Outbox> rows = outboxRepository.findPendingForUpdate(limit, cutoff);
    List<OutboxWorker.OutboxClaim> claims = new ArrayList<>();
    OffsetDateTime now = OffsetDateTime.now();
    List<Payment> payments = new ArrayList<>();
    for (Outbox row : rows) {
      row.setStatus(OutboxStatus.PROCESSING);
      row.setClaimedBy(workerId);
      row.setClaimedAt(now);
      row.setAttemptCount(row.getAttemptCount() + 1);
      Payment payment = row.getPayment();
      payment.setStatus(PaymentStatus.PROCESSING);
      payment.setLastAttemptedAt(now);
      payments.add(payment);
      String payload = toJson(paymentToRequest(payment));
      claims.add(
          new OutboxWorker.OutboxClaim(
              row.getId(),
              payment.getId(),
              payment.getIdempotencyKey(),
              payload,
              row.getAttemptCount()));
    }
    outboxRepository.saveAll(rows);
    paymentRepository.saveAll(payments);
    return claims;
  }

  @Transactional
  public void markSuccess(OutboxWorker.OutboxClaim claim, String response) {
    Optional<Payment> paymentOpt = paymentRepository.findById(claim.paymentId());
    Optional<Outbox> outboxOpt = outboxRepository.findById(claim.outboxId());
    if (paymentOpt.isEmpty() || outboxOpt.isEmpty()) {
      return;
    }

    Payment payment = paymentOpt.get();
    Outbox outbox = outboxOpt.get();
    if (outbox.getStatus() == OutboxStatus.DONE) {
      return;
    }
    payment.setStatus(PaymentStatus.COMPLETED);
    payment.setAttemptCount(payment.getAttemptCount() + 1);
    payment.setLastAttemptedAt(OffsetDateTime.now());
    payment.setExternalResponse(toJsonStringLiteral(response));

    outbox.setStatus(OutboxStatus.DONE);

    paymentRepository.save(payment);
    outboxRepository.save(outbox);
    outboxMetrics.incrementProcessed();
  }

  @Transactional
  public void markFailure(OutboxWorker.OutboxClaim claim, String reason) {
    Optional<Payment> paymentOpt = paymentRepository.findById(claim.paymentId());
    Optional<Outbox> outboxOpt = outboxRepository.findById(claim.outboxId());
    if (paymentOpt.isEmpty() || outboxOpt.isEmpty()) {
      return;
    }

    Payment payment = paymentOpt.get();
    Outbox outbox = outboxOpt.get();
    if (outbox.getStatus() == OutboxStatus.FAILED) {
      return;
    }
    int attempt = claim.attemptCount();
    boolean exhausted = attempt >= retryProperties.getMaxAttempts();

    if (exhausted) {
      markPermanentFailure(claim, reason);
      return;
    }

    payment.setAttemptCount(payment.getAttemptCount() + 1);
    payment.setLastAttemptedAt(OffsetDateTime.now());

    outbox.setStatus(OutboxStatus.PENDING);
    outbox.setNextAttemptAt(OffsetDateTime.now().plus(backoffCalculator.nextDelay(attempt)));
    outboxRepository.save(outbox);
    paymentRepository.save(payment);
  }

  @Transactional
  public void markPermanentFailure(OutboxWorker.OutboxClaim claim, String reason) {
    Optional<Payment> paymentOpt = paymentRepository.findById(claim.paymentId());
    Optional<Outbox> outboxOpt = outboxRepository.findById(claim.outboxId());
    if (paymentOpt.isEmpty() || outboxOpt.isEmpty()) {
      return;
    }

    Payment payment = paymentOpt.get();
    Outbox outbox = outboxOpt.get();
    payment.setStatus(PaymentStatus.FAILED);
    payment.setAttemptCount(payment.getAttemptCount() + 1);
    payment.setLastAttemptedAt(OffsetDateTime.now());
    payment.setFailureReason(reason);

    outbox.setStatus(OutboxStatus.FAILED);

    paymentRepository.save(payment);
    outboxRepository.save(outbox);
    outboxMetrics.incrementFailed();
  }

  private PaymentRequest paymentToRequest(Payment payment) {
    PaymentRequest request = new PaymentRequest();
    request.setAmount(payment.getAmount());
    request.setCurrency(payment.getCurrency());
    request.setPayerId(payment.getPayerId());
    request.setPayeeId(payment.getPayeeId());
    return request;
  }

  private String toJson(PaymentRequest request) {
    try {
      return objectMapper.writeValueAsString(request);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Failed to serialize payment request", ex);
    }
  }

  private String toJsonStringLiteral(String value) {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }
}