
package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.repository;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity.NotificationOutbox;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.NotificationOutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationOutboxRepository
        extends JpaRepository<NotificationOutbox, UUID>,
        NotificationOutboxRepositoryCustom {

    long countByStatus(NotificationOutboxStatus status);
}