
package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.repository;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity.NotificationOutbox;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class NotificationOutboxRepositoryImpl implements NotificationOutboxRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;


    /*
     * Claims only records that are ready for publication.
     *
     * Expired PROCESSING records are deliberately excluded.
     * They are handled by NotificationOutboxRecoveryService.
     */
    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public List<NotificationOutbox> claimAvailable(
            String workerId,
            Instant now,
            Instant leaseExpiry,
            int batchSize
    ) {

        validateClaimArguments(
                workerId,
                now,
                leaseExpiry,
                batchSize
        );

        String sql = """
                SELECT *
                FROM notification_outbox
                WHERE
                    status IN ('PENDING', 'RETRY_SCHEDULED')
                    AND available_at <= :now
                    AND attempt_count < max_attempts
                ORDER BY available_at ASC, created_at ASC
                LIMIT :batchSize
                FOR UPDATE SKIP LOCKED
                """;

        Query query = entityManager.createNativeQuery(
                sql,
                NotificationOutbox.class
        );

        query.setParameter("now", now);
        query.setParameter("batchSize", batchSize);

        List<NotificationOutbox> records = query.getResultList();

        for (NotificationOutbox record : records) {

            record.markProcessing(
                    workerId,
                    leaseExpiry
            );
        }

        entityManager.flush();

        return records;
    }


    /*
     * Finds expired leases and locks the selected rows.
     *
     * The caller must execute this method inside a transaction
     * and update the returned records before that transaction
     * commits. This keeps the row locks during recovery.
     */
    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public List<NotificationOutbox> findExpiredLeasesForRecovery(
            Instant now,
            int batchSize
    ) {

        if (now == null) {
            throw new IllegalArgumentException(
                    "Current time is required."
            );
        }

        if (batchSize < 1) {
            throw new IllegalArgumentException(
                    "Recovery batch size must be at least 1."
            );
        }

        String sql = """
                SELECT *
                FROM notification_outbox
                WHERE
                    status = 'PROCESSING'
                    AND locked_until <= :now
                ORDER BY locked_until ASC, created_at ASC
                LIMIT :batchSize
                FOR UPDATE SKIP LOCKED
                """;

        Query query = entityManager.createNativeQuery(
                sql,
                NotificationOutbox.class
        );

        query.setParameter("now", now);
        query.setParameter("batchSize", batchSize);

        return query.getResultList();
    }


    private void validateClaimArguments(
            String workerId,
            Instant now,
            Instant leaseExpiry,
            int batchSize
    ) {

        if (workerId == null || workerId.isBlank()) {
            throw new IllegalArgumentException("Worker ID is required.");
        }

        if (now == null || leaseExpiry == null || !leaseExpiry.isAfter(now)) {

            throw new IllegalArgumentException("A valid lease expiry is required.");
        }

        if (batchSize < 1) {
            throw new IllegalArgumentException("Batch size must be at least 1.");
        }
    }
}