package com.petfoster.service;

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

    public void publish(Long userId, Notification.Type type, String title, String content,
                        Long relatedId, Notification.RelatedType relatedType) {
        eventPublisher.publishEvent(new NotificationEvent(List.of(
                NotificationEvent.entry(userId, type, title, content, relatedId, relatedType)
        )));
    }

    public void publish(List<NotificationEvent.NotificationEntry> entries) {
        if (entries != null && !entries.isEmpty()) {
            eventPublisher.publishEvent(new NotificationEvent(entries));
        }
    }

    public static EntryBuilder entries() {
        return new EntryBuilder();
    }

    public static class EntryBuilder {
        private final List<NotificationEvent.NotificationEntry> entries = new ArrayList<>();

        public EntryBuilder add(Long userId, Notification.Type type, String title, String content,
                                Long relatedId, Notification.RelatedType relatedType) {
            if (userId != null) {
                entries.add(NotificationEvent.entry(userId, type, title, content, relatedId, relatedType));
            }
            return this;
        }

        public List<NotificationEvent.NotificationEntry> build() {
            return entries;
        }

        public boolean isEmpty() {
            return entries.isEmpty();
        }
    }
}
