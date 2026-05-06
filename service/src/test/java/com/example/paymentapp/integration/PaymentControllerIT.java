package com.example.paymentapp.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("it")
class PaymentControllerIT {
	@Container
	    static final PostgreSQLContainer<?> POSTGRES =
		    new PostgreSQLContainer<>("postgres:15")
			    .withDatabaseName("paymentapp-test")
					.withUsername("postgres")
					.withPassword("postgres");

	    @BeforeEach
	    void cleanDb() {
		jdbcTemplate.update("TRUNCATE TABLE outbox, payments CASCADE;");
	    }
	@DynamicPropertySource
	static void registerDataSource(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
	}

	@Autowired private TestRestTemplate restTemplate;
	@Autowired private JdbcTemplate jdbcTemplate;

	@Test
	void postPaymentPersistsPaymentAndOutbox() {
		Map<String, Object> request = new HashMap<>();
		request.put("amount", new BigDecimal("12.34"));
		request.put("currency", "USD");
		request.put("payerId", "payer-1");
		request.put("payeeId", "payee-1");

		ResponseEntity<Map> response = restTemplate.postForEntity("/payments", request, Map.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		Integer paymentCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payments", Integer.class);
		Integer outboxCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox", Integer.class);

		assertThat(paymentCount).isEqualTo(1);
		assertThat(outboxCount).isEqualTo(1);
	}

	@Test
	void postPaymentWithoutIdempotencyKeyDedupesByPayload() {
		Map<String, Object> request = new HashMap<>();
		request.put("amount", new BigDecimal("45.67"));
		request.put("currency", "usd");
		request.put("payerId", "payer-2");
		request.put("payeeId", "payee-2");

		ResponseEntity<Map> first = restTemplate.postForEntity("/payments", request, Map.class);
		ResponseEntity<Map> second = restTemplate.postForEntity("/payments", request, Map.class);

		assertThat(first.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);

		Integer paymentCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payments", Integer.class);
		Integer outboxCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox", Integer.class);

		assertThat(paymentCount).isEqualTo(1);
		assertThat(outboxCount).isEqualTo(1);
	}
}
