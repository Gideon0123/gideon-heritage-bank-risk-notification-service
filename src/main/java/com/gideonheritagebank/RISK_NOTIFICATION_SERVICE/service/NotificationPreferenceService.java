package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.service;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.dto.request.NotificationPreferenceUpdateRequest;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.dto.response.NotificationPreferenceResponse;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity.CurrentUser;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity.NotificationPreference;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.NotificationCategory;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.NotificationChannel;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.repository.NotificationPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository repository;
    private final CurrentUserService currentUserService;

    public List<NotificationPreferenceResponse> getMine() {

        CurrentUser user = currentUserService.getCurrentUser();

        return repository.findAllByUserId(user.getId())
                .stream().map(NotificationPreferenceResponse::from).toList();
    }

    @Transactional
    public NotificationPreferenceResponse upsert(
            NotificationCategory category,
            NotificationChannel channel,
            NotificationPreferenceUpdateRequest request
    ) {
        CurrentUser user = currentUserService.getCurrentUser();

        if (channel == NotificationChannel.SMS) {
            throw new IllegalArgumentException("SMS notifications are not supported by this service.");
        }

        NotificationPreference preference = repository.findByUserIdAndCategoryAndChannel(
                user.getId(), category, channel
                ).orElseGet(() -> NotificationPreference.create(user.getId(), category, channel));

        preference.updateEnabled(request.enabled());

        preference.updateQuietHours(
                request.quietHoursEnabled(),
                request.quietHoursStart(),
                request.quietHoursEnd(),
                request.timeZone()
        );

        return NotificationPreferenceResponse.from(repository.save(preference));
    }

    @Transactional
    public List<NotificationPreferenceResponse> initializeDefaults() {

        CurrentUser user = currentUserService.getCurrentUser();

        for (NotificationCategory category : NotificationCategory.values()) {

            for (NotificationChannel channel :
                    Arrays.asList(NotificationChannel.EMAIL,
                            NotificationChannel.IN_APP,
                            NotificationChannel.PUSH)) {
                if (!repository.existsByUserIdAndCategoryAndChannel(user.getId(), category, channel)) {

                    repository.save(NotificationPreference.create(user.getId(), category, channel));
                }

            }

        }

        return getMine();
    }
}
