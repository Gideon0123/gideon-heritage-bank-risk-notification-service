package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.service;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity.NotificationTemplate;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.NotificationChannel;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.NotificationContentFormat;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.repository.NotificationTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationTemplateRenderingService {

    private static final String DEFAULT_LOCALE = "en-NG";

    private static final Pattern PLACEHOLDER_PATTERN =
            Pattern.compile("\\{\\{([a-zA-Z0-9_]+)}}");

    private final NotificationTemplateRepository templateRepository;

    public RenderedNotificationTemplate render(
            String templateKey,
            NotificationChannel channel,
            Map<String, String> variables
    ) {

        if (templateKey == null || templateKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Notification template key is required."
            );
        }

        if (channel == null) {
            throw new IllegalArgumentException(
                    "Notification template channel is required."
            );
        }

        if (variables == null) {
            throw new IllegalArgumentException(
                    "Notification template variables are required."
            );
        }

        NotificationTemplate template = templateRepository
                .findFirstByTemplateKeyAndChannelAndLocaleAndActiveTrueOrderByVersionDesc(
                        templateKey,
                        channel,
                        DEFAULT_LOCALE
                )
                .orElseThrow(() -> new IllegalStateException(
                        "No active notification template found. "
                                + "templateKey=" + templateKey
                                + ", channel=" + channel
                                + ", locale=" + DEFAULT_LOCALE
                ));

        boolean html = template.getContentFormat() == NotificationContentFormat.HTML;

        String subject = renderText(
                template.getSubjectTemplate(),
                variables,
                false
        );

        /*
         * Email subjects are plain text. Strip line breaks so
         * dynamic values cannot inject additional mail headers.
         */
        if (subject != null) {
            subject = subject.replaceAll("[\\r\\n]+", " ").trim();
        }

        String title = renderText(
                template.getTitleTemplate(),
                variables,
                false
        );

        String body = renderText(
                template.getBodyTemplate(),
                variables,
                html
        );

        if (body == null || body.isBlank()) {
            throw new IllegalStateException(
                    "Rendered notification template body is empty."
            );
        }

        if (channel == NotificationChannel.EMAIL
                && (subject == null || subject.isBlank())) {

            throw new IllegalStateException(
                    "An email template must provide a subject."
            );
        }

        return new RenderedNotificationTemplate(
                template,
                subject,
                title,
                body
        );
    }

    private String renderText(
            String template,
            Map<String, String> variables,
            boolean escapeHtml
    ) {

        if (template == null) {
            return null;
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);

        StringBuffer result = new StringBuffer();

        while (matcher.find()) {

            String variableName = matcher.group(1);

            String value = variables.get(variableName);

            if (value == null) {
                throw new IllegalStateException(
                        "Missing notification template variable: "
                                + variableName
                );
            }

            if (escapeHtml) {
                value = escapeHtml(value);
            }

            matcher.appendReplacement(
                    result,
                    Matcher.quoteReplacement(value)
            );
        }

        matcher.appendTail(result);

        String rendered = result.toString();

        if (rendered.contains("{{") || rendered.contains("}}")) {
            throw new IllegalStateException(
                    "Notification template contains unresolved placeholders."
            );
        }

        return rendered;
    }

    private String escapeHtml(String value) {

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}