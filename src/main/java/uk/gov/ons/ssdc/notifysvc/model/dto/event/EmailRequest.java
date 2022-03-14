package uk.gov.ons.ssdc.notifysvc.model.dto.event;

import java.util.Map;
import java.util.UUID;
import lombok.Data;

@Data
public class EmailRequest {
  private UUID caseId;
  private String email;
  private String packCode;
  private Object uacMetadata;
  private boolean scheduled;
  private Map<String, String> personalisation;
}
