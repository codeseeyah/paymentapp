package com.example.paymentapp.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.paymentapp.client.ExternalPaymentClient;
import com.example.paymentapp.config.PaymentRetryProperties;
import com.example.paymentapp.config.PaymentWorkerProperties;
import com.example.paymentapp.metrics.OutboxMetrics;
import com.example.paymentapp.model.Outbox;
import com.example.paymentapp.model.Payment;
import com.example.paymentapp.repository.OutboxRepository;
import com.example.paymentapp.repository.PaymentRepository;
import com.example.paymentapp.service.BackoffCalculator;
import com.example.paymentapp.service.OutboxStatus;
import com.example.paymentapp.service.PaymentStatus;
import com.example.paymentapp.worker.OutboxWorker;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("it")
class MultiInstanceProcessingTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:15")
          .withDatabaseName("paymentapp")
          .withUsername("postgres")
          .withPassword("postgres");

  private static MockWebServer mockWebServer;

  @BeforeAll
  static void startServer() throws Exception {
    mockWebServer = new MockWebServer();
    mockWebServer.start();
  }

  @AfterAll
  static void stopServer() throws Exception {
    if (mockWebServer != null) {
      mockWebServer.shutdown();
    }
  }

  @DynamicPropertySource
  static void registerDataSource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("payment.external.base-url", () -> mockWebServer.url("/simulate").toString());
  }

  @Autowired private PaymentRepository paymentRepository;
  @Autowired private OutboxRepository outboxRepository;
  @Autowired private ExternalPaymentClient externalPaymentClient;
  @Autowired private BackoffCalculator backoffCalculator;
  @Autowired private PaymentRetryProperties retryProperties;
  @Autowired private PaymentWorkerProperties workerProperties;
  @Autowired private OutboxMetrics outboxMetrics;

  @Test
  void twoWorkersDoNotDoubleProcess() throws Exception {
    mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));

    Payment payment = new Payment();
    payment.setIdempotencyKey(UUID.randomUUID().toString());
    payment.setAmount(new BigDecimal("30.00"));
    payment.setCurrency("USD");
    payment.setStatus(PaymentStatus.RECEIVED);
    payment.setAttemptCount(0);
    payment = paymentRepository.save(payment);

    Outbox outbox = new Outbox();
    outbox.setPayment(payment);
    outbox.setPayload("{\"amount\":30.00,\"currency\":\"USD\"}");
    outbox.setStatus(OutboxStatus.PENDING);
    outbox.setAttemptCount(0);
    outbox.setNextAttemptAt(OffsetDateTime.now());
    outboxRepository.save(outbox);

    OutboxWorker worker1 =
        new OutboxWorker(
            outboxRepository,
            paymentRepository,
            externalPaymentClient,
            backoffCalculator,
            retryProperties,
            workerProperties,
            outboxMetrics);
    OutboxWorker worker2 =
        new OutboxWorker(
            outboxRepository,
            paymentRepository,
            externalPaymentClient,
            backoffCalculator,
            retryProperties,
            workerProperties,
            outboxMetrics);

    Thread t1 = new Thread(worker1::processOnce);
    Thread t2 = new Thread(worker2::processOnce);
    t1.start();
    t2.start();
    t1.join();
    t2.join();

    Payment updatedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
    Outbox updatedOutbox = outboxRepository.findById(outbox.getId()).orElseThrow();

    assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    assertThat(updatedOutbox.getStatus()).isEqualTo(OutboxStatus.DONE);
    assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
  }
}
