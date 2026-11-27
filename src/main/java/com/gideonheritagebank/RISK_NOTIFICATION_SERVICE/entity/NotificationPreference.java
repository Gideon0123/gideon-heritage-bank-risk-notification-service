package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.NotificationCategory;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.NotificationChannel;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(
        name = "notification_preferences",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_notification_preference_user_category_channel",
                        columnNames = {
                                "user_id",
                                "category",
                                "channel"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_notification_preferences_user",
                        columnList = "user_id"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationPreference {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    /*
     * UUID from Identity Service.
     */
    @Column(
            name = "user_id",
            nullable = false
    )
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "category",
            nullable = false,
            length = 30
    )
    private NotificationCategory category;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "channel",
            nullable = false,
            length = 20
    )
    private NotificationChannel channel;

    /*
     * Whether this user wants notifications
     * for this category through this channel.
     */
    @Column(
            name = "enabled",
            nullable = false
    )
    private boolean enabled = true;

    /*
     * Optional quiet hours.
     */
    @Column(
            name = "quiet_hours_enabled",
            nullable = false
    )
    private boolean quietHoursEnabled = false;

    @Column(name = "quiet_hours_start")
    private LocalTime quietHoursStart;

    @Column(name = "quiet_hours_end")
    private LocalTime quietHoursEnd;

    /*
     * Time zone is important because quiet hours
     * should be interpreted in the user's local time.
     */
    @Column(
            name = "time_zone",
            nullable = false,
            length = 64
    )
    private String timeZone = "Africa/Lagos";

    @CreationTimestamp
    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    @UpdateTimestamp
    @Column(
            name = "updated_at",
            nullable = false
    )
    private Instant updatedAt;

    @Version
    @Column(
            name = "version",
            nullable = false
    )
    private long version;


    // =====================================================
    // FACTORY
    // =====================================================

    public static NotificationPreference create(
            UUID userId,
            NotificationCategory category,
            NotificationChannel channel
    ) {

        if (userId == null) {
            throw new IllegalArgumentException(
                    "Preference user ID is required."
            );
        }

        if (category == null) {
            throw new IllegalArgumentException(
                    "Notification category is required."
            );
        }

        if (channel == null) {
            throw new IllegalArgumentException(
                    "Notification channel is required."
            );
        }

        NotificationPreference preference =
                new NotificationPreference();

        preference.userId = userId;
        preference.category = category;
        preference.channel = channel;
        preference.enabled = true;
        preference.quietHoursEnabled = false;
        preference.timeZone = "Africa/Lagos";

        return preference;
    }


    // =====================================================
    // PREFERENCE UPDATES
    // =====================================================

    public void updateEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void updateQuietHours(
            boolean enabled,
            LocalTime start,
            LocalTime end,
            String timeZone
    ) {

        if (enabled) {

            if (start == null || end == null) {
                throw new IllegalArgumentException(
                        "Quiet hours start and end times are required."
                );
            }

            if (start.equals(end)) {
                throw new IllegalArgumentException(
                        "Quiet hours start and end cannot be the same."
                );
            }

            if (timeZone == null || timeZone.isBlank()) {
                throw new IllegalArgumentException(
                        "Time zone is required when quiet hours are enabled."
                );
            }

            try {
                ZoneId.of(timeZone);
            } catch (Exception exception) {
                throw new IllegalArgumentException(
                        "Invalid time zone: " + timeZone
                );
            }

            this.quietHoursStart = start;
            this.quietHoursEnd = end;
            this.timeZone = timeZone;
            this.quietHoursEnabled = true;

        } else {

            this.quietHoursEnabled = false;
            this.quietHoursStart = null;
            this.quietHoursEnd = null;
        }
    }


    public Instant nextAllowedAt(Instant now) {
        if (!hasQuietHours() || now == null) return null;
        ZoneId zone = ZoneId.of(timeZone);
        ZonedDateTime localNow = now.atZone(zone);
        LocalTime current = localNow.toLocalTime();
        boolean crossesMidnight = quietHoursStart.isAfter(quietHoursEnd);
        boolean active = crossesMidnight
                ? !current.isBefore(quietHoursStart) || current.isBefore(quietHoursEnd)
                : !current.isBefore(quietHoursStart) && current.isBefore(quietHoursEnd);
        if (!active) return null;
        java.time.LocalDate endDate = localNow.toLocalDate();
        if (crossesMidnight && !current.isBefore(quietHoursStart)) endDate = endDate.plusDays(1);
        return endDate.atTime(quietHoursEnd).atZone(zone).toInstant();
    }

    public boolean hasQuietHours() {
        return quietHoursEnabled && quietHoursStart != null && quietHoursEnd != null;
    }
}