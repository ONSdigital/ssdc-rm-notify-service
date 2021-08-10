package uk.gov.ons.ssdc.notifysvc.model.dto;

import java.util.UUID;
import javax.persistence.Id;
import lombok.Data;

@Data
public class EnrichedSmsFulfilment {
  @Id private UUID caseId;
  private String telephoneNumber;
  private String uac;
  private String qid;
}
