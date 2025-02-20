package uk.gov.ons.ssdc.notifysvc.utils;

import static uk.gov.ons.ssdc.notifysvc.utils.Constants.ALLOWED_INBOUND_EVENT_SCHEMA_VERSIONS;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import uk.gov.ons.ssdc.notifysvc.model.dto.event.EventDTO;

public class JsonHelper {
  private static final ObjectMapper objectMapper = ObjectMapperFactory.objectMapper();

  public static EventDTO convertJsonBytesToEvent(byte[] bytes) {
    EventDTO event;
    try {
      event = objectMapper.readValue(bytes, EventDTO.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    if (!ALLOWED_INBOUND_EVENT_SCHEMA_VERSIONS.contains((event.getHeader().getVersion()))) {
      throw new RuntimeException(
          String.format(
              "Unsupported message version. Got %s but RM only supports %s",
              event.getHeader().getVersion(),
              String.join(", ", ALLOWED_INBOUND_EVENT_SCHEMA_VERSIONS)));
    }

    return event;
  }
}
