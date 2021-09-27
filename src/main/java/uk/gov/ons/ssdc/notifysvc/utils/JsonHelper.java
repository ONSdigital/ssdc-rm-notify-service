package uk.gov.ons.ssdc.notifysvc.utils;

import static uk.gov.ons.ssdc.notifysvc.utils.Constants.EVENT_SCHEMA_VERSION;

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

    if (!EVENT_SCHEMA_VERSION.equals(event.getHeader().getVersion())) {
      throw new RuntimeException(
          String.format(
              "Incorrect message version. Expected %s but got: %s",
              EVENT_SCHEMA_VERSION, event.getHeader()));
    }

    return event;
  }
}
