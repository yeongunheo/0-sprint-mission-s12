package com.sprint.mission.discodeit.event.listener;

import com.sprint.mission.discodeit.dto.data.MessageDto;
import com.sprint.mission.discodeit.event.NewMessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebsocketHandler {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleNewMessageEvent(NewMessageEvent event) {
        log.info("메시지 수신: {}", event.messageDto());
        MessageDto messageDto = event.messageDto();
        String destination = String.format("/sub/channels.%s.messages", messageDto.channelId());
        messagingTemplate.convertAndSend(destination, messageDto);
    }
}
