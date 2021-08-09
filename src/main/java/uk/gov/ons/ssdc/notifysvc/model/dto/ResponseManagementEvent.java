package uk.gov.ons.ssdc.notifysvc.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResponseManagementEvent {
  private EventDTO event;
  private PayloadDTO payload;
}
