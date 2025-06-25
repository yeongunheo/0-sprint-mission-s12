package com.sprint.mission.discodeit.event.listener;

import com.sprint.mission.discodeit.dto.data.BinaryContentDto;
import com.sprint.mission.discodeit.dto.data.ChannelDto;
import com.sprint.mission.discodeit.dto.data.NotificationDto;
import com.sprint.mission.discodeit.event.BinaryContentStatusUpdatedEvent;
import com.sprint.mission.discodeit.event.MultipleNotificationCreatedEvent;
import com.sprint.mission.discodeit.event.NotificationCreatedEvent;
import com.sprint.mission.discodeit.event.PrivateChannelCreatedEvent;
import com.sprint.mission.discodeit.event.PublicChannelMutationEvent;
import com.sprint.mission.discodeit.event.UserMutationEvent;
import com.sprint.mission.discodeit.service.BinaryContentService;
import com.sprint.mission.discodeit.sse.SseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class SseHandler {

    private final SseService sseService;
    private final BinaryContentService binaryContentService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(NotificationCreatedEvent event) {
        NotificationDto notification = event.notificationDto();
        UUID userId = notification.receiverId();
        sseService.send(userId, "notifications", notification);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleBinaryContent(MultipleNotificationCreatedEvent event) {
        List<NotificationDto> notifications = event.notifications();
        notifications.forEach(notification -> {
            UUID userId = notification.receiverId();
            sseService.send(userId, "notifications", notification);
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(PublicChannelMutationEvent event) {
        UUID channelId = event.mutatedChannelId();
        sseService.broadcast("channels.refresh", Map.of("channelId", channelId));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(PrivateChannelCreatedEvent event) {
        ChannelDto channel = event.channel();
        sseService.send(event.participantIds(), "channels.refresh", channel);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(UserMutationEvent event) {
        UUID userId = event.mutatedUserId();
        sseService.broadcast("users.refresh", Map.of("userId", userId));
    }

    @EventListener
    public void handle(BinaryContentStatusUpdatedEvent event) {
        UUID binaryContentId = event.binaryContentId();
        BinaryContentDto binaryContent = binaryContentService.find(binaryContentId);
        sseService.broadcast("binaryContents.status", binaryContent);
    }
}
