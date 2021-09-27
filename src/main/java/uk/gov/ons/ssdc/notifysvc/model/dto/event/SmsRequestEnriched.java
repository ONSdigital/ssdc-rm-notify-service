package uk.gov.ons.ssdc.notifysvc.model.dto.event;

import java.util.UUID;
import lombok.Data;

@Data
public class SmsRequestEnriched {
  private UUID caseId;
  private String phoneNumber;
  private String packCode;
  private String uac;
  private String qid;
}
