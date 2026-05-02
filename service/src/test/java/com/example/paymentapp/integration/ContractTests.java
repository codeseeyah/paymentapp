package com.example.paymentapp.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("it")
class ContractTests {
  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:15")
          .withDatabaseName("paymentapp")
          .withUsername("postgres")
          .withPassword("postgres");

  @DynamicPropertySource
  static void registerDataSource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void duplicateIdempotencyKeyReturnsExisting() {
    Map<String, Object> request = new HashMap<>();
    request.put("amount", new BigDecimal("44.00"));
    request.put("currency", "USD");

    HttpHeaders headers = new HttpHeaders();
    headers.add("Idempotency-Key", "contract-key-1");

    ResponseEntity<Map> first =
        restTemplate.postForEntity("/payments", new HttpEntity<>(request, headers), Map.class);
    ResponseEntity<Map> second =
        restTemplate.postForEntity("/payments", new HttpEntity<>(request, headers), Map.class);

    assertThat(first.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
  }
}
