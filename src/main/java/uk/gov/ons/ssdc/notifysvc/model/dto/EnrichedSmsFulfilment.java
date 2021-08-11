package uk.gov.ons.ssdc.notifysvc.model.dto;

import java.util.UUID;
import javax.persistence.Id;
import lombok.Data;

@Data
public class EnrichedSmsFulfilment {
  private UUID caseId;
  private String packCode;
  private String uac;
  private String qid;
}
