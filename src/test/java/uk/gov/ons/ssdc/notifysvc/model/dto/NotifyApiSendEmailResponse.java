package uk.gov.ons.ssdc.notifysvc.model.dto;

import java.util.UUID;
import lombok.Data;

@Data
public class NotifyApiSendEmailResponse {
  private UUID id;
  private UUID reference;
  private Content content;
  private Template template;

  @Data
  public class Content {
    public String subject;
    public String body;
    public String from_number;
  }

  @Data
  public class Template {
    public UUID id;
    public int version;
    public String uri;
  }
}
