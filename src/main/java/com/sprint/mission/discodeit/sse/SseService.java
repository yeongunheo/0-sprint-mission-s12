package com.sprint.mission.discodeit.sse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SseService {

    @Value("${sse.timeout:300000}")
    private long timeout;

    private final SseEmitterRepository sseEmitterRepository;
    private final SseMessageRepository sseMessageRepository;

    public SseEmitter connect(UUID receiverId, UUID lastEventId) {
        SseEmitter sseEmitter = new SseEmitter(timeout);

        sseEmitter.onCompletion(() -> {
            log.debug("sse on onCompletion");
            sseEmitterRepository.delete(receiverId, sseEmitter);
        });
        sseEmitter.onTimeout(() -> {
            log.debug("sse on onTimeout");
            sseEmitterRepository.delete(receiverId, sseEmitter);
        });
        sseEmitter.onError((ex) -> {
            log.debug("sse on onError");
            sseEmitterRepository.delete(receiverId, sseEmitter);
        });

        sseEmitterRepository.save(receiverId, sseEmitter);

        Optional.ofNullable(lastEventId)
                .ifPresent(id -> {
                    sseMessageRepository.findAllByEventIdAfterAndReceiverId(id, receiverId)
                            .forEach(sseMessage -> {
                                try {
                                    sseEmitter.send(sseMessage.toEvent());
                                } catch (IOException e) {
                                    log.error(e.getMessage(), e);
                                }
                            });
                });

        return sseEmitter;
    }

    public void send(UUID receiverId, String eventName, Object data) {
        sseEmitterRepository.findByReceiverId(receiverId)
                .ifPresent(sseEmitters -> {
                    SseMessage message = sseMessageRepository.save(
                            SseMessage.create(receiverId, eventName, data));
                    sseEmitters.forEach(sseEmitter -> {
                        try {
                            sseEmitter.send(message.toEvent());
                        } catch (IOException e) {
                            log.error(e.getMessage(), e);
                        }
                    });
                });
    }

    public void send(Collection<UUID> receiverIds, String eventName, Object data) {
        SseMessage message = sseMessageRepository.save(SseMessage.create(receiverIds, eventName, data));
        Set<ResponseBodyEmitter.DataWithMediaType> event = message.toEvent();
        sseEmitterRepository.findAllByReceiverIdsIn(receiverIds)
                .forEach(sseEmitter -> {
                    try {
                        sseEmitter.send(event);
                    } catch (IOException e) {
                        log.error(e.getMessage(), e);
                    }
                });
    }

    public void broadcast(String eventName, Object data) {
        SseMessage message = sseMessageRepository.save(SseMessage.createBroadcast(eventName, data));
        Set<ResponseBodyEmitter.DataWithMediaType> event = message.toEvent();
        sseEmitterRepository.findAll()
                .forEach(sseEmitter -> {
                    try {
                        sseEmitter.send(event);
                    } catch (IOException e) {
                        log.error(e.getMessage(), e);
                    }
                });
    }

    public void send(SseMessage sseMessage) {
        sseMessageRepository.save(sseMessage);
        Set<ResponseBodyEmitter.DataWithMediaType> event = sseMessage.toEvent();
        if (sseMessage.isBroadcast()) {
            sseEmitterRepository.findAll()
                    .forEach(sseEmitter -> {
                        try {
                            sseEmitter.send(event);
                        } catch (IOException e) {
                            log.error(e.getMessage(), e);
                        }
                    });
        } else {
            sseEmitterRepository.findAllByReceiverIdsIn(sseMessage.getReceiverIds())
                    .forEach(sseEmitter -> {
                        try {
                            sseEmitter.send(event);
                        } catch (IOException e) {
                            log.error(e.getMessage(), e);
                        }
                    });
        }
    }

    @Scheduled(cron = "0 */30 * * * *")
    public void cleanUp() {
        Set<ResponseBodyEmitter.DataWithMediaType> ping = SseEmitter.event()
                .name("ping")
                .build();
        sseEmitterRepository.findAll()
                .forEach(sseEmitter -> {
                    try {
                        sseEmitter.send(ping);
                    } catch (IOException e) {
                        log.error(e.getMessage(), e);
                        sseEmitter.completeWithError(e);
                    }
                });
    }
}
