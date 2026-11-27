package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.service;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.dto.response.NotificationDeliveryHistoryResponse;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.dto.response.PagedResponse;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity.CurrentUser;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity.NotificationDelivery;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.exception.ResourceNotFoundException;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.repository.NotificationDeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationDeliveryHistoryService {

    private final NotificationDeliveryRepository repository;
    private final CurrentUserService currentUserService;

    public PagedResponse<NotificationDeliveryHistoryResponse> getMine(int page, int size) {

        validate(page,size); CurrentUser user = currentUserService.getCurrentUser();
        Page<NotificationDelivery> result = repository.findHistoryByUserId(
                user.getId(),
                PageRequest.of(page -1 , size, Sort.by(Sort.Direction.DESC,"createdAt"))
        );

        return PagedResponse.from(result.map(NotificationDeliveryHistoryResponse::from));
    }

    public NotificationDeliveryHistoryResponse getMine(UUID id) {

        CurrentUser user=currentUserService.getCurrentUser();

        return NotificationDeliveryHistoryResponse.from(repository.findOwnedDelivery(id,user.getId())
                .orElseThrow(()->new ResourceNotFoundException("Notification delivery was not found.")));
    }


    private void validate(int page,int size) {
        if(page<1)
            throw new IllegalArgumentException("Page must be at least 1.");

        if(size<1||size>100)
            throw new IllegalArgumentException("Page size must be between 1 and 100.");
    }
}
