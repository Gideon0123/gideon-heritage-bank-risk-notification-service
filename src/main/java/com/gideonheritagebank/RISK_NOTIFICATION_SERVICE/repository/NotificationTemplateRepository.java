package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.repository;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity.NotificationTemplate;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.NotificationChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {

    Optional<NotificationTemplate>
    findFirstByTemplateKeyAndChannelAndLocaleAndActiveTrueOrderByVersionDesc(
            String templateKey,
            NotificationChannel channel,
            String locale
    );

    Optional<NotificationTemplate>
    findByTemplateKeyAndChannelAndLocaleAndVersion(
            String templateKey,
            NotificationChannel channel,
            String locale,
            int version
    );

    List<NotificationTemplate>
    findAllByTemplateKeyAndChannelAndLocaleOrderByVersionDesc(
            String templateKey,
            NotificationChannel channel,
            String locale
    );

    List<NotificationTemplate> findAllByActiveTrue();
}