package uk.gov.ons.ssdc.notifysvc.model.dto.api;

import lombok.Data;

@Data
public class Peek {
  private String messageHash;
  private byte[] messagePayload;
}
