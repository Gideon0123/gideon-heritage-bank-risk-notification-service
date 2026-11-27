package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.repository;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity.NotificationTemplate;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.NotificationChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {
    Optional<NotificationTemplate> findFirstByTemplateKeyAndChannelAndLocaleAndActiveTrueOrderByVersionDesc(String templateKey, NotificationChannel channel, String locale);
    Optional<NotificationTemplate> findByTemplateKeyAndChannelAndLocaleAndVersion(String templateKey, NotificationChannel channel, String locale, int version);
    List<NotificationTemplate> findAllByTemplateKeyAndChannelAndLocaleOrderByVersionDesc(String templateKey, NotificationChannel channel, String locale);
    List<NotificationTemplate> findAllByActiveTrue();
    List<NotificationTemplate> findAllByOrderByTemplateKeyAscChannelAscLocaleAscVersionDesc();
    @Query("SELECT COALESCE(MAX(t.version), 0) FROM NotificationTemplate t WHERE t.templateKey=:key AND t.channel=:channel AND t.locale=:locale")
    int findMaxVersion(@Param("key") String key, @Param("channel") NotificationChannel channel, @Param("locale") String locale);
    @Modifying
    @Query("UPDATE NotificationTemplate t SET t.active=false, t.updatedAt=CURRENT_TIMESTAMP WHERE t.templateKey=:key AND t.channel=:channel AND t.locale=:locale")
    int deactivateVersions(@Param("key") String key, @Param("channel") NotificationChannel channel, @Param("locale") String locale);
}
