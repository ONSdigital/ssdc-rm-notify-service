package uk.gov.ons.ssdc.notifysvc.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SmsFulfilmentResponseSuccess implements SmsFulfilmentResponse{
  private String uacHash;
  private String qid;
}
