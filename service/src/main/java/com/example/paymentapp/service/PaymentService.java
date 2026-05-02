package com.example.paymentapp.service;

import com.example.paymentapp.dto.PaymentRequest;
import com.example.paymentapp.model.Outbox;
import com.example.paymentapp.model.Payment;
import com.example.paymentapp.repository.OutboxRepository;
import com.example.paymentapp.repository.PaymentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {
  private final PaymentRepository paymentRepository;
  private final OutboxRepository outboxRepository;
  private final ObjectMapper objectMapper;

  public PaymentService(
      PaymentRepository paymentRepository,
      OutboxRepository outboxRepository,
      ObjectMapper objectMapper) {
    this.paymentRepository = paymentRepository;
    this.outboxRepository = outboxRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public PaymentCreateResult createAndQueuePayment(PaymentRequest request, String idempotencyKey) {
    Optional<Payment> existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
    if (existing.isPresent()) {
      return new PaymentCreateResult(existing.get(), false);
    }

    Payment payment = new Payment();
    payment.setIdempotencyKey(idempotencyKey);
    payment.setAmount(request.getAmount());
    payment.setCurrency(request.getCurrency());
    payment.setPayerId(request.getPayerId());
    payment.setPayeeId(request.getPayeeId());
    payment.setStatus(PaymentStatus.RECEIVED);
    payment.setAttemptCount(0);

    try {
      payment = paymentRepository.save(payment);
      Outbox outbox = new Outbox();
      outbox.setPayment(payment);
      outbox.setPayload(toJson(request));
      outbox.setStatus(OutboxStatus.PENDING);
      outbox.setAttemptCount(0);
      outbox.setNextAttemptAt(OffsetDateTime.now());
      outboxRepository.save(outbox);
      return new PaymentCreateResult(payment, true);
    } catch (DataIntegrityViolationException ex) {
      Optional<Payment> afterConflict = paymentRepository.findByIdempotencyKey(idempotencyKey);
      if (afterConflict.isPresent()) {
        return new PaymentCreateResult(afterConflict.get(), false);
      }
      throw ex;
    }
  }

  private String toJson(PaymentRequest request) {
    try {
      return objectMapper.writeValueAsString(request);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Failed to serialize payment request", ex);
    }
  }

  public record PaymentCreateResult(Payment payment, boolean created) {}
}
