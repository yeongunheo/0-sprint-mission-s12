package com.sprint.mission.discodeit.event;

import com.sprint.mission.discodeit.entity.BinaryContentUploadStatus;

import java.util.UUID;

public record BinaryContentStatusUpdatedEvent(
        UUID binaryContentId,
        BinaryContentUploadStatus status
) {

}
