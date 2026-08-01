package com.petfoster.event;

import com.petfoster.common.MoreStrings;
import com.petfoster.entity.FailedNotification;
import com.petfoster.repository.FailedNotificationRepository;
import com.petfoster.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;
    private final FailedNotificationRepository failedNotificationRepository;

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleNotificationEvent(NotificationEvent event) {
        for (NotificationEvent.NotificationEntry entry : event.getEntries()) {
            sendWithRetry(entry);
        }
    }

    private void sendWithRetry(NotificationEvent.NotificationEntry entry) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                notificationService.sendNotification(
                        entry.getUserId(),
                        entry.getType(),
                        entry.getTitle(),
                        entry.getContent(),
                        entry.getRelatedId(),
                        entry.getRelatedType()
                );
                return;
            } catch (Exception e) {
                log.warn("站内消息发送失败(第{}/{}次): userId={}, type={}, error={}",
                        attempt, MAX_RETRIES, entry.getUserId(), entry.getType(), e.getMessage());
                if (attempt < MAX_RETRIES) {
                    sleep(RETRY_DELAY_MS * attempt);
                } else {
                    log.error("站内消息发送最终失败，已转入补偿队列: userId={}, type={}, title={}",
                            entry.getUserId(), entry.getType(), entry.getTitle());
                    persistFailedNotification(entry, e);
                }
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistFailedNotification(NotificationEvent.NotificationEntry entry, Exception e) {
        FailedNotification failed = FailedNotification.builder()
                .userId(entry.getUserId())
                .type(entry.getType())
                .title(entry.getTitle())
                .content(entry.getContent())
                .relatedId(entry.getRelatedId())
                .relatedType(entry.getRelatedType())
                .retryCount(0)
                .maxRetries(MAX_RETRIES)
                .status(FailedNotification.Status.PENDING)
                .lastError(MoreStrings.truncate(e.getMessage(), 2000))
                .nextRetryAt(LocalDateTime.now().plusMinutes(5))
                .build();
        failedNotificationRepository.save(failed);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
