package com.example.paymentapp.simulator;

import com.example.paymentapp.config.PaymentSimulatorProperties;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("simulator")
public class PaymentSimulatorController {
  private final PaymentSimulatorProperties properties;

  public PaymentSimulatorController(PaymentSimulatorProperties properties) {
    this.properties = properties;
  }

  @PostMapping("/simulate")
  public ResponseEntity<Map<String, Object>> simulate(@RequestBody Map<String, Object> payload)
      throws InterruptedException {
    long minDelay = Math.max(0, properties.getMinDelayMs());
    long maxDelay = Math.max(minDelay, properties.getMaxDelayMs());
    long delay = ThreadLocalRandom.current().nextLong(minDelay, maxDelay + 1);
    Thread.sleep(delay);

    boolean shouldFail = ThreadLocalRandom.current().nextDouble() < properties.getFailureRate();
    if (shouldFail) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(Map.of("status", "failed", "delayMs", delay));
    }

    return ResponseEntity.ok(Map.of("status", "ok", "delayMs", delay));
  }
}
