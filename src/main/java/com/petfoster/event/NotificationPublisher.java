package com.petfoster.event;

import com.petfoster.entity.Notification;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 站内通知的统一发布入口：业务方只需给出接收人和文案，
 * 条目组装、空接收人过滤、空列表跳过等样板逻辑集中在这里。
 */
@Component
@RequiredArgsConstructor
public class NotificationPublisher {

    private final ApplicationEventPublisher eventPublisher;

    /**
     * 给单个用户发通知，userId 为 null 时直接跳过。
     */
    public void notify(Long userId, Notification.Type type, String title, String content,
                       Long relatedId, Notification.RelatedType relatedType) {
        if (userId == null) {
            return;
        }
        publish(List.of(NotificationEvent.entry(userId, type, title, content, relatedId, relatedType)));
    }

    /**
     * 给寄养双方（主人 + 寄养人）发相同内容的通知，可排除操作者本人。
     */
    public void notifyParties(Long ownerId, Long fostererId, Long excludeUserId,
                              Notification.Type type, String title, String content,
                              Long relatedId, Notification.RelatedType relatedType) {
        List<NotificationEvent.NotificationEntry> entries = new ArrayList<>();
        if (ownerId != null && !ownerId.equals(excludeUserId)) {
            entries.add(NotificationEvent.entry(ownerId, type, title, content, relatedId, relatedType));
        }
        if (fostererId != null && !fostererId.equals(excludeUserId)) {
            entries.add(NotificationEvent.entry(fostererId, type, title, content, relatedId, relatedType));
        }
        publish(entries);
    }

    /**
     * 批量发布，空列表直接跳过。
     */
    public void publish(List<NotificationEvent.NotificationEntry> entries) {
        if (entries != null && !entries.isEmpty()) {
            eventPublisher.publishEvent(new NotificationEvent(entries));
        }
    }
}
