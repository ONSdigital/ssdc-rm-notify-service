package uk.gov.ons.ssdc.notifysvc.model.dto;

import java.util.UUID;
import lombok.Data;

@Data
public class RequestHeaderDTO {
  private String source;
  private String channel;
  private UUID correlationId;
  private String originatingUser;
}
