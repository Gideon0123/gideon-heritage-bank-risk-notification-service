package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.NotificationChannel;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.NotificationContentFormat;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(
        name = "notification_templates",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_notification_template_version",
                        columnNames = {
                                "template_key",
                                "channel",
                                "locale",
                                "version"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_notification_template_lookup",
                        columnList = "template_key, channel, locale, active"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationTemplate {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    /*
     * Stable identifier used by the application.
     *
     * Examples:
     * identity.email-verification
     * identity.password-reset
     * identity.staff-onboarding
     */
    @Column(
            name = "template_key",
            nullable = false,
            length = 120
    )
    private String templateKey;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "channel",
            nullable = false,
            length = 20
    )
    private NotificationChannel channel;

    /*
     * Examples:
     * en-NG
     * en-US
     */
    @Column(
            name = "locale",
            nullable = false,
            length = 20
    )
    private String locale = "en-NG";

    @Column(
            name = "version",
            nullable = false
    )
    private int version;

    @Column(
            name = "active",
            nullable = false
    )
    private boolean active = false;

    @Column(
            name = "subject_template",
            length = 500
    )
    private String subjectTemplate;

    @Column(
            name = "title_template",
            length = 500
    )
    private String titleTemplate;

    @Column(
            name = "body_template",
            nullable = false,
            columnDefinition = "TEXT"
    )
    private String bodyTemplate;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "content_format",
            nullable = false,
            length = 20
    )
    private NotificationContentFormat contentFormat =
            NotificationContentFormat.PLAIN_TEXT;

    @Column(
            name = "description",
            length = 500
    )
    private String description;

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


    // =====================================================
    // FACTORY
    // =====================================================

    public static NotificationTemplate create(
            String templateKey,
            NotificationChannel channel,
            String locale,
            int version,
            String subjectTemplate,
            String titleTemplate,
            String bodyTemplate,
            NotificationContentFormat contentFormat,
            String description
    ) {

        if (templateKey == null || templateKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Template key is required."
            );
        }

        if (templateKey.length() > 120) {
            throw new IllegalArgumentException(
                    "Template key cannot exceed 120 characters."
            );
        }

        if (channel == null) {
            throw new IllegalArgumentException(
                    "Template channel is required."
            );
        }

        if (locale == null || locale.isBlank()) {
            throw new IllegalArgumentException(
                    "Template locale is required."
            );
        }

        if (version < 1) {
            throw new IllegalArgumentException(
                    "Template version must be at least 1."
            );
        }

        if (bodyTemplate == null || bodyTemplate.isBlank()) {
            throw new IllegalArgumentException(
                    "Template body is required."
            );
        }

        if (contentFormat == null) {
            throw new IllegalArgumentException(
                    "Template content format is required."
            );
        }

        if (locale.length() > 20) {
            throw new IllegalArgumentException(
                    "Template locale cannot exceed 20 characters."
            );
        }

        if (subjectTemplate != null && subjectTemplate.length() > 500) {
            throw new IllegalArgumentException(
                    "Template subject cannot exceed 500 characters."
            );
        }

        if (titleTemplate != null && titleTemplate.length() > 500) {
            throw new IllegalArgumentException(
                    "Template title cannot exceed 500 characters."
            );
        }

        NotificationTemplate template = new NotificationTemplate();

        template.templateKey = templateKey.trim();
        template.channel = channel;
        template.locale = locale.trim();
        template.version = version;
        template.subjectTemplate = subjectTemplate;
        template.titleTemplate = titleTemplate;
        template.bodyTemplate = bodyTemplate;
        template.contentFormat = contentFormat;
        template.description = description;

        return template;
    }


    // =====================================================
    // TEMPLATE STATE
    // =====================================================

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }
}