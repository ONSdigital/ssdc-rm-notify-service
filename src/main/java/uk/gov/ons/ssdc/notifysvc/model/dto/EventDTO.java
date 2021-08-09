package uk.gov.ons.ssdc.notifysvc.model.dto;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class EventDTO {
  private EventTypeDTO type;
  private String source;
  private String channel;
  private OffsetDateTime dateTime;
  private UUID transactionId;
}
