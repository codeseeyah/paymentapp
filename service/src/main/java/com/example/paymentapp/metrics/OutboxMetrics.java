package com.example.paymentapp.metrics;

import com.example.paymentapp.repository.OutboxRepository;
import com.example.paymentapp.service.OutboxStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class OutboxMetrics {
  private final Counter processedCounter;
  private final Counter failedCounter;

  public OutboxMetrics(MeterRegistry registry, OutboxRepository outboxRepository) {
    this.processedCounter = registry.counter("payments_processed_total");
    this.failedCounter = registry.counter("payments_failed_total");

    Gauge.builder("outbox_pending_gauge", outboxRepository, repo -> repo.countByStatus(OutboxStatus.PENDING))
        .register(registry);
    Gauge.builder(
            "outbox_processing_gauge", outboxRepository, repo -> repo.countByStatus(OutboxStatus.PROCESSING))
        .register(registry);
  }

  public void incrementProcessed() {
    processedCounter.increment();
  }

  public void incrementFailed() {
    failedCounter.increment();
  }
}
