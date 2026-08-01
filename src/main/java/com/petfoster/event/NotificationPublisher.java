package com.petfoster.event;

import com.petfoster.event.NotificationEvent.NotificationEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 站内通知的发布入口。
 *
 * <p>此前各业务方法都要手动 {@code new ArrayList<>()} 收集 {@link NotificationEntry}、
 * 判断非空、再 {@code eventPublisher.publishEvent(new NotificationEvent(entries))}。
 * 这里把"空列表不发布 + 包装事件对象"的样板集中处理，业务代码只需构造条目并调用 publish。
 */
@Component
@RequiredArgsConstructor
public class NotificationPublisher {

    private final ApplicationEventPublisher eventPublisher;

    /** 发布一批通知条目；列表为空则不发布。 */
    public void publish(List<NotificationEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        eventPublisher.publishEvent(new NotificationEvent(entries));
    }

    /** 发布若干条通知条目；无条目则不发布。 */
    public void publish(NotificationEntry... entries) {
        if (entries == null || entries.length == 0) {
            return;
        }
        publish(List.of(entries));
    }
}
