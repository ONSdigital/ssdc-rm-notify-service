package uk.gov.ons.ssdc.notifysvc.model.dto.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventDTO {
  private EventHeaderDTO header;
  private PayloadDTO payload;
}
