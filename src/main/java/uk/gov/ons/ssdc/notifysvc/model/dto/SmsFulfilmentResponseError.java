package uk.gov.ons.ssdc.notifysvc.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SmsFulfilmentResponseError implements SmsFulfilmentResponse{
  String error;
}
