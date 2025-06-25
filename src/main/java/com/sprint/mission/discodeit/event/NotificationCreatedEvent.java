package com.sprint.mission.discodeit.event;

import com.sprint.mission.discodeit.dto.data.NotificationDto;

import java.time.Instant;

public record NotificationCreatedEvent(
        Instant createdAt,
        NotificationDto notificationDto
) {

    public NotificationCreatedEvent(NotificationDto notificationDto) {
        this(Instant.now(), notificationDto);
    }
}
