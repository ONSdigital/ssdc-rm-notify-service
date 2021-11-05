package uk.gov.ons.ssdc.notifysvc.model.dto.event;

import lombok.Data;

import java.util.UUID;

@Data
public class EnrichedEmailFulfilment {
  private UUID caseId;
  private String packCode;
  private String uac;
  private String qid;
  private Object uacMetadata;
}
