package com.example.paymentapp.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.paymentapp.dto.PaymentRequest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PaymentIdempotencyKeyGeneratorTest {
  private final PaymentIdempotencyKeyGenerator generator =
      new PaymentIdempotencyKeyGenerator();

  @Test
  void generatesDeterministicKeyForSamePayload() {
    PaymentRequest first = new PaymentRequest();
    first.setAmount(new BigDecimal("10.00"));
    first.setCurrency("usd");
    first.setPayerId("payer-1");
    first.setPayeeId("payee-1");

    PaymentRequest second = new PaymentRequest();
    second.setAmount(new BigDecimal("10.0"));
    second.setCurrency("USD");
    second.setPayerId("payer-1");
    second.setPayeeId("payee-1");

    String key1 = generator.generate(first);
    String key2 = generator.generate(second);

    assertThat(key1).isEqualTo(key2);
    assertThat(key1).startsWith("det-");
  }
}
