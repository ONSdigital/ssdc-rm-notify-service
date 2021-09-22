package uk.gov.ons.ssdc.notifysvc.model.dto.api;

import lombok.Data;

import java.util.Map;

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
