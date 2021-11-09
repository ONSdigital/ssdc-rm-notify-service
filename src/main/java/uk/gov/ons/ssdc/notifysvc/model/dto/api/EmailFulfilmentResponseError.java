package uk.gov.ons.ssdc.notifysvc.model.dto.api;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class EmailFulfilmentResponseError implements EmailFulfilmentResponse {
  String error;
}
