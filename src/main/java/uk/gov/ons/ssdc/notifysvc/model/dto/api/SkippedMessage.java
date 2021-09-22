package uk.gov.ons.ssdc.notifysvc.model.dto.api;

import java.util.Map;
import lombok.Data;

@Data
public class SkippedMessage {
  private String messageHash;
  private byte[] messagePayload;
  private String service;
  private String subscription;
  private String routingKey;
  private String contentType;
  private Map headers;
}
