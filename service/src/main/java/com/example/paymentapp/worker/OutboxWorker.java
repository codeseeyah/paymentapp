package com.example.paymentapp.worker;

import com.example.paymentapp.client.ExternalPaymentClient;
import com.example.paymentapp.client.ExternalPaymentException;
import com.example.paymentapp.dto.PaymentRequest;
import com.example.paymentapp.config.PaymentWorkerProperties;
import java.util.List;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxWorker {
  private final ExternalPaymentClient externalPaymentClient;
  private final PaymentWorkerProperties workerProperties;
  private final OutboxClaimHandler outboxClaimHandler;

  public OutboxWorker(
      ExternalPaymentClient externalPaymentClient,
      PaymentWorkerProperties workerProperties,
      OutboxClaimHandler outboxClaimHandler) {
    this.externalPaymentClient = externalPaymentClient;
    this.workerProperties = workerProperties;
    this.outboxClaimHandler = outboxClaimHandler;
  }

  @Scheduled(fixedDelayString = "${payment.worker.poll-interval-ms:500}")
  public void scheduledPoll() {
    if (!workerProperties.isScheduled()) {
      return;
    }

    processBatch(workerProperties.getBatchSize());
  }

  public List<OutboxClaim> claimBatch(int limit) {
    return outboxClaimHandler.claimBatch(limit);
  }

  public void processOnce() {
    processBatch(workerProperties.getBatchSize());
  }

  private void processBatch(int limit) {
    List<OutboxClaim> claims = outboxClaimHandler.claimBatch(limit);
    for (OutboxClaim claim : claims) {
      processClaim(claim);
    }
  }

  private void processClaim(OutboxClaim claim) {
    try {
      String response = externalPaymentClient.submitPayment(claim.payload(), claim.idempotencyKey());
      outboxClaimHandler.markSuccess(claim, response);
    } catch (ExternalPaymentException ex) {
      if (ex.isRetryable()) {
        outboxClaimHandler.markFailure(claim, ex.getMessage());
      } else {
        outboxClaimHandler.markPermanentFailure(claim, ex.getMessage());
      }
    }
  }

  public record OutboxClaim(
      UUID outboxId,
      UUID paymentId,
      String idempotencyKey,
      String payload,
      int attemptCount) {}
}