package com.example.paymentapp.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/actuator")
public class PrometheusMetricsController {
  private final MeterRegistry meterRegistry;

  public PrometheusMetricsController(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  @GetMapping(value = "/prometheus", produces = MediaType.TEXT_PLAIN_VALUE)
  public ResponseEntity<String> scrape() {
    if (meterRegistry instanceof PrometheusMeterRegistry prometheusMeterRegistry) {
      return ResponseEntity.ok(prometheusMeterRegistry.scrape());
    }

    String body =
        meterRegistry.getMeters().stream()
            .map(meter -> meter.getId().getName())
            .distinct()
            .sorted()
            .map(name -> name + " 1")
            .reduce((left, right) -> left + "\n" + right)
            .orElse("");
    return ResponseEntity.ok(body);
  }
}