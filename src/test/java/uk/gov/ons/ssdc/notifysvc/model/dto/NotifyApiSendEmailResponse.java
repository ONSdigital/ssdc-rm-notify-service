package uk.gov.ons.ssdc.notifysvc.model.dto;

import lombok.Data;

import java.util.Optional;
import java.util.UUID;

@Data
public class NotifyApiSendEmailResponse {
  private UUID id;
  private String reference;
  private UUID notificationId;
  private UUID templateId;
  private int templateVersion;
  private String templateUri;
  private String body;
  private String subject;
  private Optional<String> fromEmail;

  @Data
  public class Content {
    public String body;
    public String from_number;
  }
}
