package com.sprint.mission.discodeit.event;

import com.sprint.mission.discodeit.dto.data.NotificationDto;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record MultipleNotificationCreatedEvent(
        Instant createdAt,
        List<NotificationDto> notifications
) {

    public MultipleNotificationCreatedEvent(List<NotificationDto> notifications) {
        this(Instant.now(), notifications);
    }

    public Set<UUID> receiverIds() {
        return notifications.stream()
                .map(NotificationDto::receiverId)
                .collect(Collectors.toSet());
    }
}
