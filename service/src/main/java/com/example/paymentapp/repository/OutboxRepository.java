package com.example.paymentapp.repository;

import com.example.paymentapp.model.Outbox;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxRepository extends JpaRepository<Outbox, UUID> {
  @Query(
      value =
          "SELECT * FROM outbox "
          + "WHERE (status = 'pending' AND next_attempt_at <= now()) "
          + "OR (status = 'processing' AND claimed_at <= :processingCutoff) "
          + "ORDER BY next_attempt_at NULLS FIRST, claimed_at NULLS FIRST "
          + "LIMIT :limit "
          + "FOR UPDATE SKIP LOCKED",
      nativeQuery = true)
    List<Outbox> findPendingForUpdate(
      @Param("limit") int limit, @Param("processingCutoff") java.time.OffsetDateTime cutoff);

    long countByStatus(String status);
}
