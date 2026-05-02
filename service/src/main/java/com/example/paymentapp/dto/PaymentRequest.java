package com.example.paymentapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class PaymentRequest {
  @NotNull
  private BigDecimal amount;

  @NotBlank
  private String currency;

  private String payerId;
  private String payeeId;

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal amount) {
    this.amount = amount;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public String getPayerId() {
    return payerId;
  }

  public void setPayerId(String payerId) {
    this.payerId = payerId;
  }

  public String getPayeeId() {
    return payeeId;
  }

  public void setPayeeId(String payeeId) {
    this.payeeId = payeeId;
  }
}
