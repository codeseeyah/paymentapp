package com.example.paymentapp.worker;

import com.example.paymentapp.client.ExternalPaymentClient;
import com.example.paymentapp.client.ExternalPaymentException;
import com.example.paymentapp.config.PaymentRetryProperties;
import com.example.paymentapp.config.PaymentWorkerProperties;
import com.example.paymentapp.model.Outbox;
import com.example.paymentapp.model.Payment;
import com.example.paymentapp.repository.OutboxRepository;
import com.example.paymentapp.repository.PaymentRepository;
import com.example.paymentapp.metrics.OutboxMetrics;
import com.example.paymentapp.service.BackoffCalculator;
import com.example.paymentapp.service.OutboxStatus;
import com.example.paymentapp.service.PaymentStatus;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxWorker {
  private final OutboxRepository outboxRepository;
  private final PaymentRepository paymentRepository;
  private final ExternalPaymentClient externalPaymentClient;
  private final BackoffCalculator backoffCalculator;
  private final PaymentRetryProperties retryProperties;
  private final PaymentWorkerProperties workerProperties;
  private final OutboxMetrics outboxMetrics;
  private final String workerId = UUID.randomUUID().toString();

  public OutboxWorker(
      OutboxRepository outboxRepository,
      PaymentRepository paymentRepository,
      ExternalPaymentClient externalPaymentClient,
      BackoffCalculator backoffCalculator,
      PaymentRetryProperties retryProperties,
      PaymentWorkerProperties workerProperties,
      OutboxMetrics outboxMetrics) {
    this.outboxRepository = outboxRepository;
    this.paymentRepository = paymentRepository;
    this.externalPaymentClient = externalPaymentClient;
    this.backoffCalculator = backoffCalculator;
    this.retryProperties = retryProperties;
    this.workerProperties = workerProperties;
    this.outboxMetrics = outboxMetrics;
  }

  @Scheduled(fixedDelayString = "${payment.worker.poll-interval-ms:500}")
  @Transactional
  public void scheduledPoll() {
    if (!workerProperties.isScheduled()) {
      return;
    }
    processOnce();
  }

  @Transactional
  public void processOnce() {
    List<OutboxClaim> claims = claimBatch(workerProperties.getBatchSize());
    for (OutboxClaim claim : claims) {
      processClaim(claim);
    }
  }

  @Transactional
  public List<OutboxClaim> claimBatch(int limit) {
    OffsetDateTime cutoff =
        OffsetDateTime.now().minusNanos(workerProperties.getLeaseTimeoutMs() * 1_000_000L);
    List<Outbox> rows = outboxRepository.findPendingForUpdate(limit, cutoff);
    List<OutboxClaim> claims = new ArrayList<>();
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
      claims.add(
          new OutboxClaim(
              row.getId(),
              payment.getId(),
              payment.getIdempotencyKey(),
              row.getPayload(),
              row.getAttemptCount()));
    }
    outboxRepository.saveAll(rows);
    // paymentRepository.saveAll(payments);
    return claims;
  }

  private void processClaim(OutboxClaim claim) {
    try {
      String response = externalPaymentClient.submitPayment(claim.payload(), claim.idempotencyKey());
      markSuccess(claim, response);
    } catch (ExternalPaymentException ex) {
      if (ex.isRetryable()) {
        markFailure(claim, ex.getMessage());
      } else {
        markPermanentFailure(claim, ex.getMessage());
      }
    }
  }

  @Transactional
  public void markSuccess(OutboxClaim claim, String response) {
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
  public void markFailure(OutboxClaim claim, String reason) {
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
  public void markPermanentFailure(OutboxClaim claim, String reason) {
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

  public record OutboxClaim(
      UUID outboxId,
      UUID paymentId,
      String idempotencyKey,
      String payload,
      int attemptCount) {}

  private String toJsonStringLiteral(String value) {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }
}
