package uk.gov.ons.ssdc.notifysvc.model.dto.api;

import lombok.Data;

@Data
public class RequestDTO {
  private RequestHeaderDTO header;
  private RequestPayloadDTO payload;
}
