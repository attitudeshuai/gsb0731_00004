package com.petfoster.service.support;

import com.petfoster.entity.Notification;
import com.petfoster.event.NotificationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class NotificationHelper {

    private final ApplicationEventPublisher eventPublisher;

    public Builder builder() {
        return new Builder();
    }

    public class Builder {
        private final List<NotificationEvent.NotificationEntry> entries = new ArrayList<>();

        public Builder add(Long userId, Notification.Type type, String title, String content,
                           Long relatedId, Notification.RelatedType relatedType) {
            if (userId != null) {
                entries.add(NotificationEvent.entry(userId, type, title, content, relatedId, relatedType));
            }
            return this;
        }

        public int publish() {
            if (entries.isEmpty()) {
                return 0;
            }
            eventPublisher.publishEvent(new NotificationEvent(entries));
            return entries.size();
        }
    }
}
