package com.part2.monew.service.impl;

import com.part2.monew.dto.request.NotificationCursorRequest;
import com.part2.monew.dto.response.CursorPageResponse;
import com.part2.monew.dto.response.NotificationResponse;
import com.part2.monew.entity.Notification;
import com.part2.monew.entity.User;
import com.part2.monew.global.exception.user.NoPermissionToUpdateException;
import com.part2.monew.repository.NotificationRepository;
import com.part2.monew.service.NotificationService;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {
    private final NotificationRepository notificationRepository;

    @Transactional
    @Override
    public Notification createNotification(User user, String content, String resourceType, UUID resourceId) {
        Notification notification = new Notification(user, content, resourceType, resourceId);

        return notificationRepository.save(notification);
    }

    @Transactional
    @Override
    public void updated(UUID notificationId, UUID userId) {
        Notification notification = notificationRepository.findById(notificationId).orElse(null);

        if(!Objects.requireNonNull(notification).getUser().getId().equals(userId)){
            throw new NoPermissionToUpdateException("알림 확인 권한이 없습니다.");
        }
        notification.setConfirmed(true);

    }

    @Transactional
    @Override
    public void updatedAll(UUID userId) {
        List<Notification> notifications = notificationRepository.findByUserIdAndConfirmedFalse(userId);
        for (Notification n : notifications) {
            n.setConfirmed(true);
        }
    }

    @Scheduled(cron = "0 0 9 * * *")
    @Transactional
    public void deleteOldConfirmedNotifications() {
        Timestamp oneWeekAgo = Timestamp.valueOf(LocalDateTime.now().minusWeeks(1));
        notificationRepository.deleteConfirmedNotificationsBefore(oneWeekAgo);
    }

    @Override
    public CursorPageResponse<NotificationResponse> getNoConfirmedNotifications(UUID userId, NotificationCursorRequest request) {
        int limit = (request.limit() != null && request.limit() > 0) ? request.limit() : 10;
        int limitPlusOne = limit + 1;

        List<Notification> notifications;
        Timestamp cursorTs = null;
        if (request.cursor() != null) {
            try {
                cursorTs = Timestamp.from(Instant.parse(request.cursor()));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        if (cursorTs == null) {
            notifications = notificationRepository.findTop51ByUserIdAndConfirmedFalseOrderByCreatedAtDesc(userId);
        } else {
            notifications = notificationRepository.findTop51ByUserIdAndConfirmedFalseAndCreatedAtLessThanOrderByCreatedAtDesc(userId, cursorTs);
        }

        boolean hasNext = notifications.size() == limitPlusOne;
        if (hasNext) {
            notifications = notifications.subList(0, limit);
        }

        List<NotificationResponse> content = notifications.stream()
                .map(n -> new NotificationResponse(
                        n.getId(),
                        n.getCreatedAt(),
                        n.getUpdatedAt(),
                        n.isConfirmed(),
                        n.getUser().getId(),
                        n.getContent(),
                        n.getResourceType(),
                        n.getResourceId()
                ))
                .collect(Collectors.toList());

        String nextCursor = (hasNext && !content.isEmpty())
                ? content.get(content.size() - 1).createdAt().toString()
                : null;

        long totalElements = notificationRepository.countByUserIdAndConfirmedFalse(userId);


        return CursorPageResponse.of(content, nextCursor, nextCursor, totalElements, hasNext);
    }
}
