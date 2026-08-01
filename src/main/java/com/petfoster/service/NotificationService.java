package com.petfoster.service;

import com.petfoster.common.BusinessException;
import com.petfoster.common.MoreStrings;
import com.petfoster.common.PageResponse;
import com.petfoster.common.PageSort;
import com.petfoster.dto.NotificationDTO;
import com.petfoster.entity.FailedNotification;
import com.petfoster.entity.Notification;
import com.petfoster.repository.FailedNotificationRepository;
import com.petfoster.repository.NotificationRepository;
import com.petfoster.util.EntityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final String DEFAULT_SORT_FIELD = "createdAt";
    private static final Map<String, String> SORT_FIELDS = Map.of(
            "createdAt", "createdAt",
            "created_at", "createdAt",
            "isRead", "isRead",
            "is_read", "isRead"
    );

    private final NotificationRepository notificationRepository;
    private final FailedNotificationRepository failedNotificationRepository;

    @Transactional
    public void sendNotification(Long userId, Notification.Type type, String title, String content,
                                  Long relatedId, Notification.RelatedType relatedType) {
        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .content(content)
                .relatedId(relatedId)
                .relatedType(relatedType)
                .isRead(false)
                .build();
        notificationRepository.save(notification);
        log.info("站内消息发送成功: userId={}, type={}, title={}", userId, type, title);
    }

    public PageResponse<NotificationDTO.NotificationResponse> getMyNotifications(
            Long userId, int page, int size, String sort, Boolean isRead) {

        var pageable = PageSort.of(page, size, sort, DEFAULT_SORT_FIELD, SORT_FIELDS);

        Page<Notification> notificationPage = isRead != null
                ? notificationRepository.findByUserIdAndIsRead(userId, isRead, pageable)
                : notificationRepository.findByUserId(userId, pageable);

        return PageResponse.from(notificationPage, EntityMapper::toNotificationResponse);
    }

    public NotificationDTO.UnreadCountResponse getUnreadCount(Long userId) {
        long count = notificationRepository.countByUserIdAndIsRead(userId, false);
        return NotificationDTO.UnreadCountResponse.builder().count(count).build();
    }

    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> BusinessException.notFound("消息不存在"));
        if (!notification.getUserId().equals(userId)) {
            throw BusinessException.forbidden("无权限操作此消息");
        }
        if (notification.getIsRead()) {
            return;
        }
        notificationRepository.markAsRead(userId, notificationId);
        log.info("消息已标记为已读: notificationId={}, userId={}", notificationId, userId);
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsRead(userId);
        log.info("所有消息已标记为已读: userId={}", userId);
    }

    @Scheduled(fixedDelay = 300000)
    @Transactional
    public void retryFailedNotifications() {
        List<FailedNotification> failedList = failedNotificationRepository
                .findByStatusAndNextRetryAtBefore(FailedNotification.Status.PENDING, LocalDateTime.now());

        for (FailedNotification failed : failedList) {
            try {
                failed.setStatus(FailedNotification.Status.RETRYING);
                failedNotificationRepository.save(failed);

                sendNotification(
                        failed.getUserId(),
                        failed.getType(),
                        failed.getTitle(),
                        failed.getContent(),
                        failed.getRelatedId(),
                        failed.getRelatedType()
                );

                failed.setStatus(FailedNotification.Status.RESOLVED);
                failedNotificationRepository.save(failed);
                log.info("补偿消息发送成功: failedId={}, userId={}", failed.getId(), failed.getUserId());
            } catch (Exception e) {
                int newCount = failed.getRetryCount() + 1;
                failed.setRetryCount(newCount);
                failed.setLastError(MoreStrings.truncate(e.getMessage(), 2000));

                if (newCount >= failed.getMaxRetries()) {
                    failed.setStatus(FailedNotification.Status.DEAD);
                    log.error("补偿消息最终死亡: failedId={}, userId={}, 已重试{}次",
                            failed.getId(), failed.getUserId(), newCount);
                } else {
                    failed.setStatus(FailedNotification.Status.PENDING);
                    failed.setNextRetryAt(LocalDateTime.now().plusMinutes(10));
                    log.warn("补偿消息重试失败: failedId={}, 第{}次, 将在10分钟后重试",
                            failed.getId(), newCount);
                }
                failedNotificationRepository.save(failed);
            }
        }
    }

    @Transactional
    public int retryAllDeadNotifications() {
        List<FailedNotification> deadList = failedNotificationRepository
                .findByStatus(FailedNotification.Status.DEAD);

        int recovered = 0;
        for (FailedNotification failed : deadList) {
            try {
                sendNotification(
                        failed.getUserId(),
                        failed.getType(),
                        failed.getTitle(),
                        failed.getContent(),
                        failed.getRelatedId(),
                        failed.getRelatedType()
                );

                failed.setStatus(FailedNotification.Status.RESOLVED);
                failedNotificationRepository.save(failed);
                recovered++;
                log.info("手动恢复死亡消息成功: failedId={}, userId={}", failed.getId(), failed.getUserId());
            } catch (Exception e) {
                failed.setRetryCount(failed.getRetryCount() + 1);
                failed.setLastError(MoreStrings.truncate(e.getMessage(), 2000));
                failedNotificationRepository.save(failed);
                log.error("手动恢复死亡消息失败: failedId={}, userId={}", failed.getId(), failed.getUserId());
            }
        }
        return recovered;
    }
}
