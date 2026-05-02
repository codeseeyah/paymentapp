package com.example.paymentapp.client;

import com.example.paymentapp.config.PaymentExternalProperties;
import java.util.Objects;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class ExternalPaymentClient {
  private final RestTemplate restTemplate;
  private final PaymentExternalProperties externalProperties;

  public ExternalPaymentClient(
      RestTemplate restTemplate, PaymentExternalProperties externalProperties) {
    this.restTemplate = restTemplate;
    this.externalProperties = externalProperties;
  }

  public String submitPayment(String payload, String idempotencyKey) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
      headers.add("Idempotency-Key", idempotencyKey);
    }

    HttpEntity<String> request = new HttpEntity<>(payload, headers);
    try {
      ResponseEntity<String> response =
          restTemplate.postForEntity(externalProperties.getBaseUrl(), request, String.class);
      if (!response.getStatusCode().is2xxSuccessful()) {
        throw new ExternalPaymentException(
            "Non-2xx response: " + response.getStatusCode(), true);
      }
      return Objects.toString(response.getBody(), "");
    } catch (RestClientException ex) {
      throw new ExternalPaymentException("Payment call failed", true, ex);
    }
  }
}
