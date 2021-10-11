package uk.gov.ons.ssdc.notifysvc.testUtils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.messaging.Message;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.EventDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.EventHeaderDTO;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.PayloadDTO;
import uk.gov.ons.ssdc.notifysvc.utils.Constants;

public class MessageConstructor {
  public static Message<byte[]> constructMessageWithValidTimeStamp(Object payload) {
    byte[] payloadBytes = JsonTestHelper.convertObjectToJson(payload).getBytes();
    return constructMessageInternal(payloadBytes);
  }

  private static <T> Message<T> constructMessageInternal(T msgPayload) {
    Message<T> message = mock(Message.class);
    when(message.getPayload()).thenReturn(msgPayload);
    return message;
  }

  public static EventDTO buildEventDTO(String topic) {
    EventDTO eventDTO = new EventDTO();
    EventHeaderDTO eventHeaderDTO = new EventHeaderDTO();
    PayloadDTO payloadDTO = new PayloadDTO();
    eventHeaderDTO.setTopic(topic);
    eventHeaderDTO.setDateTime(OffsetDateTime.now());
    eventHeaderDTO.setMessageId(UUID.randomUUID());
    eventHeaderDTO.setCorrelationId(UUID.randomUUID());
    eventHeaderDTO.setOriginatingUser("test@example.test");
    eventHeaderDTO.setSource("TEST_SOURCE");
    eventHeaderDTO.setChannel("TEST_CHANNEL");
    eventHeaderDTO.setVersion(Constants.OUTBOUND_EVENT_SCHEMA_VERSION);

    eventDTO.setPayload(payloadDTO);
    eventDTO.setHeader(eventHeaderDTO);
    return eventDTO;
  }
}
