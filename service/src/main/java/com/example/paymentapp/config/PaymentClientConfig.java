package com.example.paymentapp.config;

import java.time.Duration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class PaymentClientConfig {
  @Bean
  public RestTemplate paymentRestTemplate(
      RestTemplateBuilder builder, PaymentExternalProperties externalProperties) {
    return builder
        .setConnectTimeout(Duration.ofMillis(externalProperties.getConnectTimeoutMs()))
        .setReadTimeout(Duration.ofMillis(externalProperties.getReadTimeoutMs()))
        .build();
  }
}
