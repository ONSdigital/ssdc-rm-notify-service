package uk.gov.ons.ssdc.notifysvc.model.dto.event;

import lombok.Data;

import java.util.UUID;

@Data
public class EmailRequestEnriched {
  private UUID caseId;
  private String email;
  private String packCode;
  private String uac;
  private String qid;
}
