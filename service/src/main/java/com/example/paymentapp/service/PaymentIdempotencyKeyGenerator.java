package com.example.paymentapp.service;

import com.example.paymentapp.dto.PaymentRequest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class PaymentIdempotencyKeyGenerator {
  public String generate(PaymentRequest request) {
    String payload =
        normalize(request.getAmount())
            + "|"
            + normalize(request.getCurrency())
            + "|"
            + normalize(request.getPayerId())
            + "|"
            + normalize(request.getPayeeId());
    return "det-" + sha256Hex(payload);
  }

  private String normalize(BigDecimal value) {
    if (value == null) {
      return "";
    }
    return value.stripTrailingZeros().toPlainString();
  }

  private String normalize(String value) {
    if (value == null) {
      return "";
    }
    return value.trim().toUpperCase();
  }

  private String sha256Hex(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(hash.length * 2);
      for (byte b : hash) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 not available", ex);
    }
  }
}
