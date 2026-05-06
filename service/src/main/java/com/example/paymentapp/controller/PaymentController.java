package com.example.paymentapp.controller;

import com.example.paymentapp.dto.PaymentRequest;
import com.example.paymentapp.dto.PaymentResource;
import com.example.paymentapp.model.Payment;
import com.example.paymentapp.repository.PaymentRepository;
import com.example.paymentapp.service.PaymentIdempotencyKeyGenerator;
import com.example.paymentapp.service.PaymentService;
import com.example.paymentapp.service.PaymentService.PaymentCreateResult;
import jakarta.validation.Valid;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/payments")
public class PaymentController {
  private final PaymentService paymentService;
  private final PaymentRepository paymentRepository;
  private final PaymentIdempotencyKeyGenerator keyGenerator =
      new PaymentIdempotencyKeyGenerator();

  public PaymentController(PaymentService paymentService, PaymentRepository paymentRepository) {
    this.paymentService = paymentService;
    this.paymentRepository = paymentRepository;
  }

  @PostMapping
  public ResponseEntity<PaymentResource> createPayment(
      @Valid @RequestBody PaymentRequest request,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
    String effectiveKey =
      idempotencyKey != null ? idempotencyKey : keyGenerator.generate(request);
    PaymentCreateResult result = paymentService.createAndQueuePayment(request, effectiveKey);
    PaymentResource resource = PaymentResource.fromPayment(result.payment());
    HttpStatus status = result.created() ? HttpStatus.ACCEPTED : HttpStatus.OK;
    return ResponseEntity.status(status).body(resource);
  }

  @GetMapping("/{paymentId}")
  public ResponseEntity<PaymentResource> getPayment(@PathVariable UUID paymentId) {
    Optional<Payment> payment = paymentRepository.findById(paymentId);
    return payment
        .map(value -> ResponseEntity.ok(PaymentResource.fromPayment(value)))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @GetMapping
  public ResponseEntity<List<PaymentResource>> listPayments(
          @RequestParam(defaultValue = "50") int limit) {
      List<PaymentResource> payments = paymentRepository.findAll(
              PageRequest.of(0, Math.min(limit, 200)))
              .stream()
              .map(PaymentResource::fromPayment)
              .toList();
      return ResponseEntity.ok(payments);
  }
}
