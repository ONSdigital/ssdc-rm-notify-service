package uk.gov.ons.ssdc.notifysvc.model.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class SmsRequest {
  private UUID caseId;
  private String phoneNumber;
  private String packCode;
}
