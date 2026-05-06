package com.example.paymentapp.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("it")
class SchemaMigrationTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:15")
          .withDatabaseName("paymentapp-test")
          .withUsername("postgres")
          .withPassword("postgres");

  @DynamicPropertySource
  static void registerDataSource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanDb() {
    jdbcTemplate.update("TRUNCATE TABLE outbox, payments CASCADE;");
  }

  @Test
  void migrationCreatesExpectedIndexes() {
    Integer outboxIndex =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'outbox' AND indexname = 'idx_outbox_pending_next_attempt'",
            Integer.class);
    Integer paymentsIndex =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'payments' AND indexname = 'ux_payments_idempotency_key'",
            Integer.class);

    assertThat(outboxIndex).isEqualTo(1);
    assertThat(paymentsIndex).isEqualTo(1);
  }
}
