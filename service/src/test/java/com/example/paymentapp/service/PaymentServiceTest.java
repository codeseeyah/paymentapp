package com.example.paymentapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.paymentapp.dto.PaymentRequest;
import com.example.paymentapp.model.Payment;
import com.example.paymentapp.repository.OutboxRepository;
import com.example.paymentapp.repository.PaymentRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PaymentServiceTest {
  private final PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
  private final OutboxRepository outboxRepository = Mockito.mock(OutboxRepository.class);
  private final PaymentService paymentService = new PaymentService(paymentRepository, outboxRepository);

  @Test
  void createAndQueuePaymentCreatesOutboxWhenNew() {
    PaymentRequest request = new PaymentRequest();
    request.setAmount(new BigDecimal("12.34"));
    request.setCurrency("USD");

    when(paymentRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
    when(paymentRepository.save(any(Payment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    PaymentService.PaymentCreateResult result =
        paymentService.createAndQueuePayment(request, "key-1");

    assertThat(result.created()).isTrue();
    verify(outboxRepository).save(any());
  }

  @Test
  void createAndQueuePaymentReturnsExistingOnDuplicate() {
    Payment existing = new Payment();
    existing.setIdempotencyKey("key-1");
    when(paymentRepository.findByIdempotencyKey("key-1"))
        .thenReturn(Optional.of(existing));

    PaymentService.PaymentCreateResult result =
        paymentService.createAndQueuePayment(new PaymentRequest(), "key-1");

    assertThat(result.created()).isFalse();
    verify(paymentRepository, never()).save(any());
    verify(outboxRepository, never()).save(any());
  }
}
