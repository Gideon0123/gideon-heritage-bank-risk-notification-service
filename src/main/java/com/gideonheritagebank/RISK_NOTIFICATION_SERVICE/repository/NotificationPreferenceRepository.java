package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.repository;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity.NotificationPreference;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.NotificationCategory;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.NotificationChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationPreferenceRepository
        extends JpaRepository<NotificationPreference, UUID> {

    List<NotificationPreference> findAllByUserId(
            UUID userId
    );

    Optional<NotificationPreference> findByUserIdAndCategoryAndChannel(
            UUID userId,
            NotificationCategory category,
            NotificationChannel channel
    );

    boolean existsByUserIdAndCategoryAndChannel(
            UUID userId,
            NotificationCategory category,
            NotificationChannel channel
    );

    void deleteAllByUserId(UUID userId);
}